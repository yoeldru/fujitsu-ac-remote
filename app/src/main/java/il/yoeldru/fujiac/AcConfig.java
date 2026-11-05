// נכתב: 2026-11-05 10:30 (שעון ישראל)
// הגדרות של מזגן אחד: שם, השלט החכם (RM4) שלו, דגם שלט, קוד שלט (A-D) והמצב האחרון.
package il.yoeldru.fujiac;

import org.json.JSONObject;

import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public class AcConfig {
    public int id;
    public String name = "מזגן";
    public String host = "";
    public String mac = "";
    public int devtype = 0x51DA;
    public String devName = "";
    public int model = FujitsuIr.MODEL_ARRAH2E;
    public boolean alwaysPower = false;
    /** קוד השלט של פוג'יטסו: 0=A (ברירת מחדל), 1=B, 2=C, 3=D. */
    public int remoteId = 0;
    public AcState state = new AcState();

    public static final String[] REMOTE_IDS = {"A", "B", "C", "D"};

    public boolean hasDevice() { return host != null && !host.isEmpty(); }

    public BroadlinkDevice device() {
        if (!hasDevice()) return null;
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
            o.put("id", id); o.put("name", name); o.put("host", host); o.put("mac", mac);
            o.put("devtype", devtype); o.put("devName", devName); o.put("model", model);
            o.put("alwaysPower", alwaysPower); o.put("remoteId", remoteId);
            o.put("state", state.toJson());
        } catch (Exception ignored) {}
        return o;
    }

    public static AcConfig fromJson(JSONObject o) {
        AcConfig a = new AcConfig();
        if (o == null) return a;
        a.id = o.optInt("id");
        a.name = o.optString("name", "מזגן");
        a.host = o.optString("host", "");
        a.mac = o.optString("mac", "");
        a.devtype = o.optInt("devtype", 0x51DA);
        a.devName = o.optString("devName", "");
        a.model = o.optInt("model", FujitsuIr.MODEL_ARRAH2E);
        a.alwaysPower = o.optBoolean("alwaysPower", false);
        a.remoteId = o.optInt("remoteId", 0);
        a.state = AcState.fromJson(o.optJSONObject("state"));
        return a;
    }
}
