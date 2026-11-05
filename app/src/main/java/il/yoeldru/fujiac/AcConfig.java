// נכתב: 2026-11-05 10:30 (שעון ישראל) · עודכן: 2026-11-05 23:22 - סוג מזגן: IR או Wi-Fi, קישור לענן
// הגדרות של מזגן אחד: שם, השלט החכם (RM4) שלו, דגם שלט, קוד שלט (A-D) והמצב האחרון.
package il.yoeldru.fujiac;

import org.json.JSONObject;

import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public class AcConfig {
    public int id;
    /** "ir" = מזגן שנשלט ב-IR דרך RM4 (פוג'יטסו); "aux" = מזגן Wi-Fi מבוסס Broadlink (טורנדו/AUX). */
    public String kind = "ir";
    public String name = "מזגן";
    public String host = "";
    public String mac = "";
    public int devtype = 0x51DA;
    public String devName = "";
    /** מזהה המזגן בענן (endpointId) - אם מקושר, שולטים דרכו כשאין חיבור מקומי. */
    public String cloudDid = "";
    public int model = FujitsuIr.MODEL_ARRAH2E;
    public boolean alwaysPower = false;
    /** קוד השלט של פוג'יטסו: 0=A (ברירת מחדל), 1=B, 2=C, 3=D. */
    public int remoteId = 0;
    public AcState state = new AcState();

    public static final String[] REMOTE_IDS = {"A", "B", "C", "D"};

    public boolean isWifi() { return "aux".equals(kind); }

    public boolean hasDevice() { return (host != null && !host.isEmpty()) || hasCloud(); }

    public boolean hasLocal() { return host != null && !host.isEmpty(); }

    public boolean hasCloud() { return cloudDid != null && !cloudDid.isEmpty(); }

    /** MAC בסדר תצוגה (hex קטן) - להשוואה מול הענן. */
    public String macDisplayHex() {
        if (mac == null || mac.length() != 12) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 10; i >= 0; i -= 2) sb.append(mac, i, i + 2);
        return sb.toString().toLowerCase();
    }

    public BroadlinkDevice device() {
        if (!hasLocal()) return null;
        BroadlinkDevice d = new BroadlinkDevice(host, FujitsuIr.unhex(mac), devtype);
        d.name = devName;
        return d;
    }

    public void setDevice(BroadlinkDevice d) {
        host = d.host;
        mac = FujitsuIr.hex(d.macRaw);
        devtype = d.devtype;
        devName = d.name;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id); o.put("kind", kind); o.put("name", name); o.put("host", host); o.put("mac", mac);
            o.put("devtype", devtype); o.put("devName", devName); o.put("model", model);
            o.put("alwaysPower", alwaysPower); o.put("remoteId", remoteId); o.put("cloudDid", cloudDid);
            o.put("state", state.toJson());
        } catch (Exception ignored) {}
        return o;
    }

    public static AcConfig fromJson(JSONObject o) {
        AcConfig a = new AcConfig();
        if (o == null) return a;
        a.id = o.optInt("id");
        a.kind = o.optString("kind", "ir");
        a.name = o.optString("name", "מזגן");
        a.host = o.optString("host", "");
        a.mac = o.optString("mac", "");
        a.devtype = o.optInt("devtype", 0x51DA);
        a.devName = o.optString("devName", "");
        a.model = o.optInt("model", FujitsuIr.MODEL_ARRAH2E);
        a.alwaysPower = o.optBoolean("alwaysPower", false);
        a.remoteId = o.optInt("remoteId", 0);
        a.cloudDid = o.optString("cloudDid", "");
        a.state = AcState.fromJson(o.optJSONObject("state"));
        return a;
    }
}
