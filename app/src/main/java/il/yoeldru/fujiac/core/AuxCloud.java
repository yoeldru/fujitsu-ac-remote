// נכתב: 2026-11-05 23:22 (שעון ישראל)
// חיבור לענן של מזגני AUX / טורנדו (פלטפורמת BroadLink DNA) - שליטה גם מחוץ לבית.
// מבוסס על broadlinkac2mqtt (auxcloud) ו-ha-aux-cloud: התחברות, רשימת מכשירים, קריאה וכתיבה של פרמטרים.
package il.yoeldru.fujiac.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AuxCloud {

    /** פרופיל אפליקציה: כל אפליקציה ממותגת של BroadLink מזדהה עם רישיון וחברה משלה. */
    public static class Profile {
        public final String name, license, licenseId, companyId, appVersion;
        public Profile(String name, String license, String licenseId, String companyId, String appVersion) {
            this.name = name; this.license = license; this.licenseId = licenseId;
            this.companyId = companyId; this.appVersion = appVersion;
        }
    }

    public static final Profile AC_FREEDOM = new Profile("AC Freedom",
            "PAFbJJ3WbvDxH5vvWezXN5BujETtH/iuTtIIW5CE/SeHN7oNKqnEajgljTcL0fBQQWM0XAAAAAAnBhJyhMi7zIQMsUcwR/PEwGA3uB5HLOnr+xRrci+FwHMkUtK7v4yo0ZHa+jPvb6djelPP893k7SagmffZmOkLSOsbNs8CAqsu8HuIDs2mDQAAAAA=",
            "3c015b249dd66ef0f11f9bef59ecd737", "48eb1b36cf0202ab2ef07b880ecda60d", "2.2.10.456537160");

    public static final String[] SERVERS = {
            "https://app-service-deu-f0e9ebbb.smarthomecs.de",
            "https://app-service-usa-fd7cc04c.smarthomecs.com",
            "https://app-service-chn-31a93883.ibroadlink.com",
            "https://app-service-rus-b8bbc3be.smarthomecs.com",
    };

    static final String TS_KEY = "kdixkdqp54545^#*";
    static final String PW_KEY = "4969fj#k23#";
    static final String BODY_KEY = "xgx3d*fe3478$ukx";
    static final byte[] IV = {(byte) 234, (byte) 170, (byte) 170, 58, (byte) 187, 88, 98, (byte) 162,
            25, 24, (byte) 181, 119, 29, 22, 21, (byte) 170};

    public static class Device {
        public String did, name, pid, mac, devSession, cookie;
        /** MAC מנורמל: 12 תווי hex קטנים בסדר התצוגה. */
        public String macNorm() { return mac == null ? "" : mac.replaceAll("[^0-9a-fA-F]", "").toLowerCase(); }
    }

    final Profile profile;
    String server;
    final String user, password;
    String loginSession = "", userId = "";
    final Map<String, Device> devices = new HashMap<>();

    public AuxCloud(Profile profile, String server, String user, String password) {
        this.profile = profile;
        this.server = server;
        this.user = user.trim();
        this.password = password;
    }

    public String server() { return server; }
    public boolean loggedIn() { return !loginSession.isEmpty(); }

    // ---------- התחברות ----------

    /** מתחבר. אם לא הוגדר שרת - מנסה את כל השרתים עד שאחד מצליח. */
    public synchronized void login() throws IOException {
        if (server != null && !server.isEmpty()) {
            loginAt(server);
            return;
        }
        IOException last = null;
        for (String s : SERVERS) {
            try {
                loginAt(s);
                server = s;
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("ההתחברות נכשלה");
    }

    void loginAt(String srv) throws IOException {
        long ts = System.currentTimeMillis() / 1000;
        boolean phone = !user.contains("@");
        // סדר שדות קבוע - ה-token מחושב על המחרוזת המדויקת
        String json = "{\"" + (phone ? "phone" : "email") + "\":" + JSONObject.quote(user)
                + ",\"password\":\"" + hex(sha1(password + PW_KEY)) + "\""
                + ",\"companyid\":\"" + profile.companyId + "\""
                + ",\"lid\":\"" + profile.licenseId + "\"}";
        Map<String, String> h = baseHeaders();
        h.put("timestamp", String.valueOf(ts));
        h.put("token", hex(md5(json + BODY_KEY)));
        byte[] body = aesZeroPad(md5(ts + TS_KEY), json.getBytes(StandardCharsets.UTF_8));
        JSONObject r = post(srv, "/account/login", body, h);
        String ls = r.optString("loginsession", ""), uid = r.optString("userid", "");
        if (ls.isEmpty() || uid.isEmpty()) {
            int st = r.optInt("status", r.optInt("error", 0));
            String m = r.optString("msg", "");
            throw new IOException("ההתחברות נכשלה (" + st + (m.isEmpty() ? "" : ": " + m) + ")");
        }
        loginSession = ls;
        userId = uid;
        this.server = srv;
    }

    // ---------- מכשירים ----------

    public synchronized List<Device> discover() throws IOException {
        if (!loggedIn()) login();
        JSONObject fam = authed("/appsync/group/member/getfamilylist", new byte[0], null);
        if (fam.optInt("status", -1) != 0) {
            // ייתכן שה-session פג
            login();
            fam = authed("/appsync/group/member/getfamilylist", new byte[0], null);
        }
        List<Device> out = new ArrayList<>();
        JSONObject fd = fam.optJSONObject("data");
        JSONArray families = fd == null ? null : fd.optJSONArray("familyList");
        if (families == null) return out;
        for (int i = 0; i < families.length(); i++) {
            String fid = families.optJSONObject(i).optString("familyid", "");
            if (fid.isEmpty()) continue;
            Map<String, String> extra = new HashMap<>();
            extra.put("familyid", fid);
            JSONObject r = authed("/appsync/group/dev/query?action=select",
                    "{\"pids\":[]}".getBytes(StandardCharsets.UTF_8), extra);
            JSONObject data = r.optJSONObject("data");
            JSONArray eps = data == null ? null : data.optJSONArray("endpoints");
            if (eps == null) continue;
            for (int k = 0; k < eps.length(); k++) {
                JSONObject e = eps.optJSONObject(k);
                Device d = new Device();
                d.did = e.optString("endpointId");
                d.name = e.optString("friendlyName");
                d.pid = e.optString("productId");
                d.mac = e.optString("mac");
                d.devSession = e.optString("devSession");
                d.cookie = e.optString("cookie");
                if (d.pid.isEmpty() || d.mac.isEmpty()) continue;
                out.add(d);
                devices.put(d.did, d);
            }
        }
        return out;
    }

    Device device(String did) throws IOException {
        Device d = devices.get(did);
        if (d == null) {
            discover();
            d = devices.get(did);
        }
        if (d == null) throw new IOException("המזגן לא נמצא בחשבון הענן");
        return d;
    }

    // ---------- פרמטרים ----------

    public synchronized Map<String, Integer> get(String did) throws IOException {
        return withRetry(did, d -> parse(control(d, "get", new ArrayList<>(), new ArrayList<>())));
    }

    public synchronized void set(String did, Map<String, Integer> params) throws IOException {
        withRetry(did, d -> {
            List<String> keys = new ArrayList<>();
            List<Integer> vals = new ArrayList<>();
            for (Map.Entry<String, Integer> e : params.entrySet()) { keys.add(e.getKey()); vals.add(e.getValue()); }
            control(d, "set", keys, vals);
            return null;
        });
    }

    interface Op { Map<String, Integer> run(Device d) throws IOException; }

    Map<String, Integer> withRetry(String did, Op op) throws IOException {
        if (!loggedIn()) login();
        try {
            return op.run(device(did));
        } catch (IOException first) {
            // session או devSession פגו - מתחברים מחדש ומרעננים את רשימת המכשירים
            login();
            devices.clear();
            return op.run(device(did));
        }
    }

    JSONObject control(Device d, String act, List<String> params, List<Integer> values) throws IOException {
        try {
            JSONObject ck = new JSONObject(new String(java.util.Base64.getDecoder().decode(d.cookie), StandardCharsets.UTF_8));
            JSONObject devObj = new JSONObject();
            devObj.put("id", ck.optString("terminalid"));
            devObj.put("key", ck.optString("aeskey"));
            devObj.put("devSession", d.devSession);
            devObj.put("aeskey", ck.optString("aeskey"));
            devObj.put("did", d.did);
            devObj.put("pid", d.pid);
            devObj.put("mac", d.mac);
            JSONObject mapped = new JSONObject();
            mapped.put("device", devObj);

            JSONObject header = new JSONObject();
            header.put("namespace", "DNA.KeyValueControl");
            header.put("name", "KeyValueControl");
            header.put("interfaceVersion", "2");
            header.put("senderId", "sdk");
            header.put("messageId", d.did + "-" + System.currentTimeMillis());

            JSONObject paired = new JSONObject();
            paired.put("did", d.did);
            paired.put("pid", d.pid);
            paired.put("mac", d.mac);
            paired.put("devicetypeflag", 0);
            paired.put("cookie", java.util.Base64.getEncoder().encodeToString(
                    mapped.toString().getBytes(StandardCharsets.UTF_8)));

            JSONObject endpoint = new JSONObject();
            endpoint.put("devicePairedInfo", paired);
            endpoint.put("endpointId", d.did);
            endpoint.put("cookie", new JSONObject());
            endpoint.put("devSession", d.devSession);

            JSONArray vals = new JSONArray();
            if ("get".equals(act) && params.size() == 1) {
                vals.put(new JSONArray().put(new JSONObject().put("val", 0).put("idx", 1)));
            }
            for (int i = 0; i < values.size(); i++)
                vals.put(new JSONArray().put(new JSONObject().put("val", values.get(i)).put("idx", i + 1)));
            JSONObject payload = new JSONObject();
            payload.put("act", act);
            payload.put("params", new JSONArray(params));
            payload.put("vals", vals);
            payload.put("did", d.did);

            JSONObject directive = new JSONObject();
            directive.put("header", header);
            directive.put("endpoint", endpoint);
            directive.put("payload", payload);
            JSONObject req = new JSONObject();
            req.put("directive", directive);

            JSONObject r = authed("/device/control/v2/sdkcontrol?license=" + URLEncoder.encode(profile.license, "UTF-8"),
                    req.toString().getBytes(StandardCharsets.UTF_8), null);
            JSONObject ev = r.optJSONObject("event");
            if (ev == null) throw new IOException("תשובה לא צפויה מהענן");
            JSONObject evh = ev.optJSONObject("header"), evp = ev.optJSONObject("payload");
            if (evh != null && "ErrorResponse".equals(evh.optString("name"))) {
                String m = evp == null ? "" : evp.optString("message", evp.optString("type", ""));
                throw new IOException("הענן החזיר שגיאה: " + m);
            }
            String data = evp == null ? "" : evp.optString("data", "");
            if (data.isEmpty()) throw new IOException("המזגן לא הגיב (ייתכן שהוא מנותק מהרשת)");
            return new JSONObject(data);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("שגיאה בתקשורת עם הענן: " + e.getMessage());
        }
    }

    static Map<String, Integer> parse(JSONObject data) {
        Map<String, Integer> m = new HashMap<>();
        JSONArray ps = data.optJSONArray("params"), vs = data.optJSONArray("vals");
        if (ps == null) return m;
        for (int i = 0; i < ps.length(); i++) {
            int v = 0;
            if (vs != null && i < vs.length()) {
                JSONArray a = vs.optJSONArray(i);
                if (a != null && a.length() > 0) v = a.optJSONObject(0).optInt("val", 0);
            }
            m.put(ps.optString(i), v);
        }
        return m;
    }

    // ---------- HTTP ----------

    Map<String, String> baseHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/x-java-serialized-object");
        h.put("licenseId", profile.licenseId);
        h.put("lid", profile.licenseId);
        h.put("language", "en");
        h.put("appVersion", profile.appVersion);
        h.put("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; SM-G991B Build/SP1A.210812.016)");
        h.put("system", "android");
        h.put("appPlatform", "android");
        h.put("Accept", "application/json");
        return h;
    }

    JSONObject authed(String path, byte[] body, Map<String, String> extra) throws IOException {
        Map<String, String> h = baseHeaders();
        h.put("loginsession", loginSession);
        h.put("userid", userId);
        if (extra != null) h.putAll(extra);
        return post(server, path, body, h);
    }

    static JSONObject post(String srv, String path, byte[] body, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(srv + path).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setDoOutput(true);
        for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        try (OutputStream o = c.getOutputStream()) { o.write(body); }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            text = b.toString("UTF-8");
        }
        if (code == 401 || code == 403) throw new IOException("אין הרשאה (" + code + ")");
        if (code >= 400) throw new IOException("שגיאת שרת " + code);
        try {
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (Exception e) {
            throw new IOException("תשובה לא תקינה מהענן");
        }
    }

    // ---------- הצפנה ----------

    static byte[] md5(String s) { return digest("MD5", s); }
    static byte[] sha1(String s) { return digest("SHA-1", s); }

    static byte[] digest(String alg, String s) {
        try { return MessageDigest.getInstance(alg).digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    static byte[] aesZeroPad(byte[] key, byte[] data) throws IOException {
        try {
            int pad = 16 - data.length % 16;
            byte[] p = java.util.Arrays.copyOf(data, data.length + pad);
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV));
            return c.doFinal(p);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    // ---------- מיפוי פרמטרים ----------
    // ac_mode: 0 קירור, 1 חימום, 2 ייבוש, 3 מאוורר, 4 אוטומטי
    // ac_mark: 0 אוטו, 1 נמוך, 2 בינוני, 3 גבוה, 4 טורבו, 5 שקט
    // temp: מעלות ×10; envtemp: טמפרטורת החדר ×10; ac_vdir/ac_hdir: נדנוד 1/0

}
