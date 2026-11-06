package com.example.render

import android.opengl.GLES20

object Shaders {

    /**
     * Part A4 & D1/D2: Unified PBR-Lite Material Shader with:
     * - Top-left UV origin (no flip)
     * - sRGB -> Linear conversion in vertex shader for DRM vertex colours & paint colour
     * - Hardware sRGB -> Linear texture sampling (GL_SRGB8_ALPHA8 on ES 3.0, or shader fallback if ES 2.0)
     * - Tangent-space normal mapping (uNormalTex + uNormalMapParams, +Y up OpenGL convention)
     * - ACES tone-mapping and gamma correction (pow(1/2.2)) applied ONCE at the end.
     */
    const val LIT_VERTEX_SHADER = """
        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;
        attribute vec3 aVertColor;
        attribute float aPaintMask;
        attribute vec3 aTangent;
        varying vec3 vWorldPos;
        varying vec3 vNormal;
        varying vec3 vTangent;
        varying vec2 vTexCoord;
        varying vec3 vVertColorLinear;
        varying vec3 vObjPos;
        varying float vPaint;
        varying float vShade;
        uniform vec3 uPartOffset;

        vec3 srgbToLinear(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(2.2));
        }

        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            // v7 paint mask: > 0.5 = paintable, value = baked panel shade (keeps the model's
            // light/dark facets when recoloured). Paint itself is resolved per fragment (liveries).
            vPaint = step(0.5, aPaintMask);
            vShade = clamp(aPaintMask, 0.6, 1.4);
            vObjPos = aPosition + uPartOffset;
            vWorldPos = worldPos.xyz;
            vec3 worldNorm = (uModelMatrix * vec4(aNormal, 0.0)).xyz;
            vNormal = normalize(worldNorm);
            vec3 worldTan = (uModelMatrix * vec4(aTangent, 0.0)).xyz;
            vTangent = normalize(worldTan);
            vTexCoord = aTexCoord;
            vVertColorLinear = srgbToLinear(aVertColor);
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val LIT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uBaseColorTex;
        uniform sampler2D uEnvEquirectTex;
        uniform sampler2D uNormalTex;
        uniform vec4 uBaseColorFactor;
        uniform vec4 uTintColor;
        uniform vec4 uMaterialParams; // x=metallic, y=roughness, z=alphaCutoff, w=hueShiftRad
        uniform vec4 uNormalParams;   // x=normalStrength, y=texIsHardwareSrgb, z=envIsHardwareSrgb, w=lite shading (v7.5)
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
        varying vec3 vTangent;
        varying vec2 vTexCoord;
        varying vec3 vVertColorLinear;
        varying vec3 vObjPos;
        varying float vPaint;
        varying float vShade;
        uniform vec3 uPaintColor;
        uniform vec3 uLiveryColor;
        uniform vec4 uLivery;     // x=mode (0 solid, 1 two-tone, 2 stripes, 3 fade), y=splitY, z=stripeHalfW, w=fade 1/len
        uniform vec3 uPaintMat;   // x=metallic, y=roughness, z=enabled

        vec3 srgbToLinear(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(2.2));
        }

        vec3 linearToSrgb(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
        }

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
            vec3 texLinear = (uNormalParams.y > 0.5) ? texSample.rgb : srgbToLinear(texSample.rgb);
            vec3 factorLinear = srgbToLinear(uBaseColorFactor.rgb);
            vec3 tintLinear = srgbToLinear(uTintColor.rgb);

            float alpha = texSample.a * uBaseColorFactor.a * uTintColor.a;
            if (uMaterialParams.z > 0.0 && alpha < uMaterialParams.z) {
                discard;
            }

            vec3 paintS = uPaintColor;
            if (uLivery.x > 0.5 && uLivery.x < 1.5) {
                float edge = smoothstep(uLivery.y - 0.015, uLivery.y + 0.015, vObjPos.y);
                paintS = mix(uLiveryColor, uPaintColor, edge);
            } else if (uLivery.x > 1.5 && uLivery.x < 2.5) {
                float ax = abs(vObjPos.x);
                float stripe = 1.0 - smoothstep(uLivery.z - 0.012, uLivery.z + 0.012, ax);
                float gap = 1.0 - smoothstep(uLivery.z * 0.28 - 0.01, uLivery.z * 0.28 + 0.01, ax);
                paintS = mix(uPaintColor, uLiveryColor, stripe * (1.0 - gap));
            } else if (uLivery.x > 2.5) {
                float t = clamp(vObjPos.z * uLivery.w + 0.5, 0.0, 1.0);
                paintS = mix(uLiveryColor, uPaintColor, smoothstep(0.15, 0.85, t));
            }
            vec3 baseLinear = mix(vVertColorLinear, srgbToLinear(clamp(paintS * vShade, 0.0, 1.0)), vPaint);
            vec3 albedo = texLinear * factorLinear * baseLinear * tintLinear;
            if (abs(uMaterialParams.w) > 0.01) {
                albedo = clamp(shiftHue(albedo, uMaterialParams.w), 0.0, 1.0);
            }

            float paintSel = vPaint * step(0.5, uPaintMat.z);
            float metallic = clamp(mix(uMaterialParams.x, uPaintMat.x, paintSel), 0.0, 1.0);
            float roughness = clamp(mix(uMaterialParams.y, uPaintMat.y, paintSel), 0.06, 1.0);

            vec3 N = normalize(vNormal);
            vec3 norm = N;
            float normalStrength = uNormalParams.x;
            if (normalStrength > 0.01) {
                vec3 T = normalize(vTangent - N * dot(vTangent, N));
                vec3 B = cross(N, T);
                vec3 mapN = texture2D(uNormalTex, vTexCoord).xyz * 2.0 - 1.0;
                mapN.xy *= normalStrength;
                norm = normalize(T * mapN.x + B * mapN.y + N * max(mapN.z, 0.1));
            }

            vec3 viewDir = normalize(uCameraPos - vWorldPos);
            vec3 lightDir = normalize(uKeyLightDir);
            vec3 halfDir = normalize(lightDir + viewDir);

            // 1. Hemisphere ambient (in linear space)
            float hemi = norm.y * 0.5 + 0.5;
            vec3 skyLinear = srgbToLinear(uSkyAmbient);
            vec3 groundLinear = srgbToLinear(uGroundAmbient);
            vec3 ambientLight = mix(groundLinear, skyLinear, hemi);

            // 2. Key directional light (Lambert + GGX-lite specular + Schlick Fresnel)
            float nDotL = max(dot(norm, lightDir), 0.0);
            float nDotV = max(dot(norm, viewDir), 0.001);
            float nDotH = max(dot(norm, halfDir), 0.0);

            float shininess = mix(128.0, 8.0, roughness);
            float specStrength = mix(0.85, 0.08, roughness) * (0.35 + 0.65 * metallic);
            float specTerm = pow(nDotH, shininess) * specStrength;

            float fresnel = pow(1.0 - nDotV, 4.0) * (1.0 - roughness * 0.7);
            vec3 diffuseColor = albedo * (1.0 - metallic * 0.65);
            vec3 specColor = mix(vec3(1.0), albedo, metallic);
            vec3 keyLinear = srgbToLinear(uKeyLightColor);

            vec3 lit = diffuseColor * (ambientLight * 1.15 + keyLinear * nDotL * 1.25)
                     + specColor * keyLinear * (specTerm + fresnel * 0.25);

            bool lite = uNormalParams.w > 0.5;
            // v7 car paint: clear-coat highlight on glossy painted panels + pearl colour flip
            float clear = lite ? 0.0 : paintSel * (1.0 - smoothstep(0.30, 0.75, roughness));
            lit += keyLinear * (pow(nDotH, 240.0) * 1.1 + fresnel * 0.10) * clear;
            if (paintSel > 0.5 && uPaintMat.z > 1.5) {
                lit *= mix(vec3(1.0), vec3(0.80, 0.95, 1.22), clamp(fresnel * 2.2, 0.0, 1.0));
            }

            // 3. Warm Desk Lamp / Point Light contribution (skipped on the lite path)
            if (!lite) {
            vec3 toPoint = uPointLightPos - vWorldPos;
            float distSq = dot(toPoint, toPoint);
            float atten = 1.0 / (1.0 + distSq * 0.00038);
            vec3 ptDir = toPoint * inversesqrt(max(distSq, 0.01));
            float ptDiff = max(dot(norm, ptDir), 0.0);
            vec3 ptLinear = srgbToLinear(uPointLightColor);
            lit += diffuseColor * ptLinear * (ptDiff * atten * 0.75);
            }

            // 4. Environment reflection from equirectangular room backdrop on shiny surfaces
            float reflectWeight = clamp((1.0 - roughness) * 0.35 + metallic * 0.28, 0.0, 0.52);
            if (reflectWeight > 0.05 && !lite) {
                vec3 refl = reflect(-viewDir, norm);
                float envU = atan(refl.z, refl.x) * 0.1591549 + 0.5;
                float envV = 0.5 - asin(clamp(refl.y, -1.0, 1.0)) * 0.3183099;
                vec3 envSample = texture2D(uEnvEquirectTex, vec2(envU, envV)).rgb;
                vec3 envLinear = (uNormalParams.z > 0.5) ? envSample : srgbToLinear(envSample);
                lit = mix(lit, envLinear * specColor, reflectWeight * (0.4 + 0.6 * fresnel));
            }

            // 5. Subtle distance haze towards horizon (linear space)
            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp((camDist - 180.0) / 280.0, 0.0, 0.48);
            vec3 fogLinear = srgbToLinear(uFogColor);
            vec3 finalLinear = mix(lit, fogLinear, fogFactor);

            // 6. ACES tone-mapping and gamma ONCE at output
            vec3 mapped = acesToneMap(finalLinear * 1.15);
            vec3 outSrgb = linearToSrgb(mapped);
            gl_FragColor = vec4(outSrgb, alpha);
        }
    """

    /**
     * Equirectangular Backdrop Sphere Shader (drawn first, depth write off).
     * Handles hardware sRGB texture sampling and applies tone-map + gamma once at output.
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
        uniform float uIsHardwareSrgb;
        varying vec2 vTexCoord;

        vec3 srgbToLinear(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(2.2));
        }

        vec3 linearToSrgb(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
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
            vec3 raw = texture2D(uBackdropTex, vTexCoord).rgb;
            vec3 lin = (uIsHardwareSrgb > 0.5) ? raw : srgbToLinear(raw);
            vec3 tinted = lin * srgbToLinear(uTint);
            vec3 mapped = acesToneMap(tinted);
            gl_FragColor = vec4(linearToSrgb(mapped), 1.0);
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
