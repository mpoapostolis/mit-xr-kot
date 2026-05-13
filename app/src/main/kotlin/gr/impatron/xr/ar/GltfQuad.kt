package gr.impatron.xr.ar

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generates a self-contained binary glTF (.glb) with a single textured quad in
 * the XY plane (+Z forward). All chunks are 4-byte aligned (Filament requires
 * this) — see https://github.com/KhronosGroup/glTF/tree/main/specification/2.0
 *
 * width = 1.0; height = 1.0 / aspect.
 */
object GltfQuad {
    fun build(pngBytes: ByteArray, aspect: Float = 1f): ByteArray {
        val w = 0.5f
        val h = (0.5f / aspect.coerceAtLeast(0.01f))

        // Vertex positions (vec3) — 4 vertices
        val posBuf = ByteBuffer.allocate(4 * 3 * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(-w); putFloat(-h); putFloat(0f)
            putFloat(w); putFloat(-h); putFloat(0f)
            putFloat(w); putFloat(h); putFloat(0f)
            putFloat(-w); putFloat(h); putFloat(0f)
        }.array()
        // UVs (vec2)
        val uvBuf = ByteBuffer.allocate(4 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0f); putFloat(1f)
            putFloat(1f); putFloat(1f)
            putFloat(1f); putFloat(0f)
            putFloat(0f); putFloat(0f)
        }.array()
        // Indices (ushort, 6 = 2 triangles, CCW so front faces +Z)
        val idxBuf = ByteBuffer.allocate(6 * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0); putShort(1); putShort(2)
            putShort(0); putShort(2); putShort(3)
        }.array()

        val bin = ByteArrayOutputStream().apply {
            write(posBuf)
            write(uvBuf)
            write(idxBuf)
            while (size() % 4 != 0) write(0)
        }
        val imageOffset = bin.size()
        bin.write(pngBytes)
        while (bin.size() % 4 != 0) bin.write(0)
        val binBytes = bin.toByteArray()

        val json = buildJsonObject {
            putJsonObject("asset") {
                put("version", "2.0")
                put("generator", "xr-native")
            }
            put("scene", 0)
            putJsonArray("scenes") {
                add(buildJsonObject { putJsonArray("nodes") { add(JsonPrimitive(0)) } })
            }
            putJsonArray("nodes") {
                add(buildJsonObject { put("mesh", 0) })
            }
            putJsonArray("meshes") {
                add(buildJsonObject {
                    putJsonArray("primitives") {
                        add(buildJsonObject {
                            putJsonObject("attributes") {
                                put("POSITION", 0)
                                put("TEXCOORD_0", 1)
                            }
                            put("indices", 2)
                            put("material", 0)
                        })
                    }
                })
            }
            putJsonArray("materials") {
                add(buildJsonObject {
                    put("name", "mat")
                    putJsonObject("pbrMetallicRoughness") {
                        putJsonObject("baseColorTexture") { put("index", 0) }
                        put("metallicFactor", 0)
                        put("roughnessFactor", 1)
                    }
                    putJsonArray("emissiveFactor") {
                        add(JsonPrimitive(1))
                        add(JsonPrimitive(1))
                        add(JsonPrimitive(1))
                    }
                    putJsonObject("emissiveTexture") { put("index", 0) }
                    put("alphaMode", "BLEND")
                    put("doubleSided", true)
                })
            }
            putJsonArray("textures") {
                add(buildJsonObject {
                    put("source", 0)
                    put("sampler", 0)
                })
            }
            putJsonArray("samplers") {
                add(buildJsonObject {
                    put("magFilter", 9729)
                    put("minFilter", 9987)
                    put("wrapS", 33071)
                    put("wrapT", 33071)
                })
            }
            putJsonArray("images") {
                add(buildJsonObject {
                    put("bufferView", 3)
                    put("mimeType", "image/png")
                })
            }
            putJsonArray("accessors") {
                add(buildJsonObject {
                    put("bufferView", 0)
                    put("byteOffset", 0)
                    put("componentType", 5126)
                    put("count", 4)
                    put("type", "VEC3")
                    putJsonArray("max") {
                        add(JsonPrimitive(w))
                        add(JsonPrimitive(h))
                        add(JsonPrimitive(0))
                    }
                    putJsonArray("min") {
                        add(JsonPrimitive(-w))
                        add(JsonPrimitive(-h))
                        add(JsonPrimitive(0))
                    }
                })
                add(buildJsonObject {
                    put("bufferView", 1)
                    put("byteOffset", 0)
                    put("componentType", 5126)
                    put("count", 4)
                    put("type", "VEC2")
                })
                add(buildJsonObject {
                    put("bufferView", 2)
                    put("byteOffset", 0)
                    put("componentType", 5123)
                    put("count", 6)
                    put("type", "SCALAR")
                })
            }
            putJsonArray("bufferViews") {
                add(buildJsonObject {
                    put("buffer", 0)
                    put("byteOffset", 0)
                    put("byteLength", posBuf.size)
                    put("byteStride", 12)
                    put("target", 34962)
                })
                add(buildJsonObject {
                    put("buffer", 0)
                    put("byteOffset", posBuf.size)
                    put("byteLength", uvBuf.size)
                    put("byteStride", 8)
                    put("target", 34962)
                })
                add(buildJsonObject {
                    put("buffer", 0)
                    put("byteOffset", posBuf.size + uvBuf.size)
                    put("byteLength", idxBuf.size)
                    put("target", 34963)
                })
                add(buildJsonObject {
                    put("buffer", 0)
                    put("byteOffset", imageOffset)
                    put("byteLength", pngBytes.size)
                })
            }
            putJsonArray("buffers") {
                add(buildJsonObject { put("byteLength", binBytes.size) })
            }
        }

        val jsonStr = Json.encodeToString(JsonObject.serializer(), json)
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
        // pad json to 4 with spaces
        val jsonPadded = ByteArrayOutputStream().apply {
            write(jsonBytes)
            while (size() % 4 != 0) write(0x20)
        }.toByteArray()

        val totalLength = 12 + 8 + jsonPadded.size + 8 + binBytes.size
        val out = ByteArrayOutputStream(totalLength)

        fun ByteArrayOutputStream.writeU32LE(v: Int) {
            write(v and 0xff)
            write((v ushr 8) and 0xff)
            write((v ushr 16) and 0xff)
            write((v ushr 24) and 0xff)
        }

        // GLB header
        out.writeU32LE(0x46546C67) // 'glTF'
        out.writeU32LE(2)
        out.writeU32LE(totalLength)
        // JSON chunk
        out.writeU32LE(jsonPadded.size)
        out.writeU32LE(0x4E4F534A) // 'JSON'
        out.write(jsonPadded)
        // BIN chunk
        out.writeU32LE(binBytes.size)
        out.writeU32LE(0x004E4942) // 'BIN\0'
        out.write(binBytes)

        return out.toByteArray()
    }
}
