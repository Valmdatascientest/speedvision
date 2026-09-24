package fr.speedvision.geometry

import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.domain.CameraCalibration
import fr.speedvision.domain.PlateProfile
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Strict versioned interchange. No images, automatic storage or source rebinding. */
object CalibrationJson {
    private fun number(
        o: JSONObject,
        key: String,
    ): Double {
        val value = o.get(key)
        require(value is Number && value.toDouble().isFinite())
        return value.toDouble()
    }

    private fun integer(
        o: JSONObject,
        key: String,
    ): Int {
        val value = number(o, key)
        require(value == value.toInt().toDouble())
        return value.toInt()
    }

    fun bindingJson(b: CalibrationBinding): JSONObject =
        JSONObject()
            .put("sourceId", b.sourceId)
            .put("nativeWidth", b.nativeWidth)
            .put("nativeHeight", b.nativeHeight)
            .put("cropLeft", b.cropLeft)
            .put("cropTop", b.cropTop)
            .put("width", b.width)
            .put("height", b.height)
            .put("rotation", b.rotation)

    fun decode(text: String): CameraCalibration {
        require(text.length <= 32_768) { "Profil trop volumineux." }
        val parser = JSONTokener(text)
        val o = parser.nextValue() as JSONObject
        require(parser.nextClean() == '\u0000')
        require(integer(o, "schemaVersion") == 1 && o.getString("model") == "brown5-native")
        val b = o.getJSONObject("binding")
        val p = o.getJSONObject("plate")

        val d = o.getJSONArray("distortion")
        return CameraCalibration(
            CalibrationBinding(
                b.getString("sourceId"),
                integer(b, "nativeWidth"),
                integer(b, "nativeHeight"),
                integer(b, "cropLeft"),
                integer(b, "cropTop"),
                integer(b, "width"),
                integer(b, "height"),
                integer(b, "rotation"),
            ),
            number(o, "fx"),
            number(o, "fy"),
            number(o, "cx"),
            number(o, "cy"),
            List(d.length()) { i ->
                val value = d.get(i)
                require(value is Number)
                value.toDouble()
            },
            o.getString("provenance"),
            number(o, "validationRmsPx"),
            PlateProfile(p.getString("name"), number(p, "widthMeters"), number(p, "heightMeters")),
        )
    }

    fun encode(c: CameraCalibration): String {
        val b = c.binding
        return JSONObject()
            .put("schemaVersion", 1)
            .put("model", "brown5-native")
            .put(
                "binding",
                JSONObject()
                    .put("sourceId", b.sourceId)
                    .put("nativeWidth", b.nativeWidth)
                    .put("nativeHeight", b.nativeHeight)
                    .put("cropLeft", b.cropLeft)
                    .put("cropTop", b.cropTop)
                    .put("width", b.width)
                    .put("height", b.height)
                    .put("rotation", b.rotation),
            ).put("fx", c.fx)
            .put("fy", c.fy)
            .put("cx", c.cx)
            .put("cy", c.cy)
            .put("distortion", JSONArray(c.distortion))
            .put("provenance", c.provenance)
            .put("validationRmsPx", c.validationRmsPx)
            .put(
                "plate",
                JSONObject()
                    .put("name", c.plate.name)
                    .put("widthMeters", c.plate.widthMeters)
                    .put("heightMeters", c.plate.heightMeters),
            ).toString(2)
    }
}
