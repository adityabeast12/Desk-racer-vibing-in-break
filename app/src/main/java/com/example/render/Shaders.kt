package com.example.render

import android.opengl.GLES20

object Shaders {

    /**
     * Part C1: Unified PBR-Lite Material Shader for both DRM2 (textured + metallic/roughness + alphaMask + hueShift)
     * and DRM1 / procedural geometry (vertex colours + paintMask + tiled textures).
     */
    const val LIT_VERTEX_SHADER = """
        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uPaintColor;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;
        attribute vec3 aVertColor;
        attribute float aPaintMask;
        varying vec3 vWorldPos;
        varying vec3 vNormal;
        varying vec2 vTexCoord;
        varying vec3 vVertColor;

        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            vec3 worldNorm = (uModelMatrix * vec4(aNormal, 0.0)).xyz;
            vNormal = normalize(worldNorm);
            vTexCoord = aTexCoord;
            vVertColor = mix(aVertColor, uPaintColor, clamp(aPaintMask, 0.0, 1.0));
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val LIT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uBaseColorTex;
        uniform sampler2D uEnvEquirectTex;
        uniform vec4 uBaseColorFactor;
        uniform vec4 uTintColor;
        uniform vec4 uMaterialParams; // x=metallic, y=roughness, z=alphaCutoff, w=hueShiftRad
        uniform vec3 uSkyAmbient;
        uniform vec3 uGroundAmbient;
        uniform vec3 uKeyLightDir;
        uniform vec3 uKeyLightColor;
        uniform vec3 uPointLightPos;
        uniform vec3 uPointLightColor;
        uniform vec3 uFogColor;
        uniform vec3 uCameraPos;

        varying vec3 vWorldPos;
        varying vec3 vNormal;
        varying vec2 vTexCoord;
        varying vec3 vVertColor;

        vec3 shiftHue(vec3 col, float angleRad) {
            float c = cos(angleRad);
            float s = sin(angleRad);
            vec3 k = vec3(0.57735, 0.57735, 0.57735);
            return col * c + cross(k, col) * s + k * dot(k, col) * (1.0 - c);
        }

        vec3 acesToneMap(vec3 x) {
            float a = 2.51;
            float b = 0.03;
            float c = 2.43;
            float d = 0.59;
            float e = 0.14;
            return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
        }

        void main() {
            vec4 texSample = texture2D(uBaseColorTex, vTexCoord);
            vec4 baseCol = texSample * uBaseColorFactor * vec4(vVertColor, 1.0) * uTintColor;
            if (uMaterialParams.z > 0.0 && baseCol.a < uMaterialParams.z) {
                discard;
            }

            vec3 albedo = baseCol.rgb;
            if (abs(uMaterialParams.w) > 0.01) {
                albedo = clamp(shiftHue(albedo, uMaterialParams.w), 0.0, 1.0);
            }

            float metallic = clamp(uMaterialParams.x, 0.0, 1.0);
            float roughness = clamp(uMaterialParams.y, 0.08, 1.0);

            vec3 norm = normalize(vNormal);
            vec3 viewDir = normalize(uCameraPos - vWorldPos);
            vec3 lightDir = normalize(uKeyLightDir);
            vec3 halfDir = normalize(lightDir + viewDir);

            // 1. Hemisphere ambient
            float hemi = norm.y * 0.5 + 0.5;
            vec3 ambientLight = mix(uGroundAmbient, uSkyAmbient, hemi);

            // 2. Key directional light (Lambert + GGX-lite specular + Schlick Fresnel)
            float nDotL = max(dot(norm, lightDir), 0.0);
            float nDotV = max(dot(norm, viewDir), 0.001);
            float nDotH = max(dot(norm, halfDir), 0.0);

            float shininess = mix(96.0, 8.0, roughness);
            float specStrength = mix(0.65, 0.08, roughness) * (0.35 + 0.65 * metallic);
            float specTerm = pow(nDotH, shininess) * specStrength;

            float fresnel = pow(1.0 - nDotV, 4.0) * (1.0 - roughness * 0.7);
            vec3 diffuseColor = albedo * (1.0 - metallic * 0.65);
            vec3 specColor = mix(vec3(1.0), albedo, metallic);

            vec3 lit = diffuseColor * (ambientLight + uKeyLightColor * nDotL * 0.78)
                     + specColor * uKeyLightColor * (specTerm + fresnel * 0.22);

            // 3. Warm Desk Lamp / Point Light contribution
            vec3 toPoint = uPointLightPos - vWorldPos;
            float distSq = dot(toPoint, toPoint);
            float atten = 1.0 / (1.0 + distSq * 0.00038);
            vec3 ptDir = toPoint * inversesqrt(max(distSq, 0.01));
            float ptDiff = max(dot(norm, ptDir), 0.0);
            lit += diffuseColor * uPointLightColor * (ptDiff * atten * 0.55);

            // 4. Environment reflection from equirectangular room backdrop on shiny surfaces
            float reflectWeight = clamp((1.0 - roughness) * 0.35 + metallic * 0.25, 0.0, 0.48);
            if (reflectWeight > 0.06) {
                vec3 refl = reflect(-viewDir, norm);
                float envU = atan(refl.z, refl.x) * 0.1591549 + 0.5;
                float envV = 0.5 - asin(clamp(refl.y, -1.0, 1.0)) * 0.3183099;
                vec3 envRgb = texture2D(uEnvEquirectTex, vec2(envU, envV)).rgb;
                lit = mix(lit, envRgb * specColor, reflectWeight * (0.4 + 0.6 * fresnel));
            }

            // 5. Subtle distance haze towards horizon
            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp((camDist - 160.0) / 260.0, 0.0, 0.55);
            vec3 finalLinear = mix(lit, uFogColor, fogFactor);
            vec3 mapped = acesToneMap(finalLinear * 1.08);
            gl_FragColor = vec4(mapped, baseCol.a);
        }
    """

    /**
     * Part C3: Equirectangular Backdrop Sphere Shader (drawn first, depth write off).
     */
    const val BACKDROP_VERTEX_SHADER = """
        uniform mat4 uMvpMatrix;
        attribute vec3 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;

        void main() {
            vTexCoord = aTexCoord;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val BACKDROP_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uBackdropTex;
        uniform vec3 uTint;
        varying vec2 vTexCoord;

        void main() {
            vec3 col = texture2D(uBackdropTex, vTexCoord).rgb * uTint;
            gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * Unlit Shader: Used for blob/contact AO shadows, boost flames, drift sparks, confetti, and dust particles.
     */
    const val UNLIT_VERTEX_SHADER = """
        uniform mat4 uMvpMatrix;
        attribute vec3 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;

        void main() {
            vColor = aColor;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val UNLIT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec4 uTintColor;
        varying vec4 vColor;

        void main() {
            gl_FragColor = vColor * uTintColor;
        }
    """

    fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }
}
