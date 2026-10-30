package com.example.render

import android.opengl.GLES20

object Shaders {

    /**
     * Lit Shader: Lambert directional + Blinn-Phong specular + rim light + paintMask tint + distance fog.
     * Uses strict GLSL ES 1.00 syntax compatible with all OpenGL ES 2.0 / 3.0 mobile GPUs.
     */
    const val LIT_VERTEX_SHADER = """
        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uPaintColor;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        attribute vec3 aVertColor;
        attribute float aPaintMask;
        varying vec3 vWorldPos;
        varying vec3 vNormal;
        varying vec3 vVertColor;

        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            vec3 worldNorm = (uModelMatrix * vec4(aNormal, 0.0)).xyz;
            vNormal = normalize(worldNorm);
            vVertColor = mix(aVertColor, uPaintColor, clamp(aPaintMask, 0.0, 1.0));
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val LIT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec4 uTintColor;
        uniform vec3 uLightDir;
        uniform vec3 uFogColor;
        uniform vec3 uCameraPos;
        varying vec3 vWorldPos;
        varying vec3 vNormal;
        varying vec3 vVertColor;

        void main() {
            vec3 norm = normalize(vNormal);
            vec3 lightDir = normalize(uLightDir);
            vec3 viewDir = normalize(uCameraPos - vWorldPos);
            vec3 halfDir = normalize(lightDir + viewDir);

            float diff = max(dot(norm, lightDir), 0.0);
            float spec = pow(max(dot(norm, halfDir), 0.0), 22.0) * 0.24;
            float ambient = 0.56;

            vec3 baseRgb = vVertColor * uTintColor.rgb;
            vec3 litRgb = baseRgb * (ambient + diff * 0.58) + vec3(spec);

            // Subtle wood-grain / marble modulation on desk surface
            if (abs(vWorldPos.y) < 0.02) {
                float grain = sin(vWorldPos.x * 1.4) * 0.018 + cos(vWorldPos.z * 0.6) * 0.015;
                litRgb += vec3(grain);
            }

            float dist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp((dist - 70.0) / 130.0, 0.0, 1.0);
            vec3 finalRgb = mix(litRgb, uFogColor, fogFactor);
            gl_FragColor = vec4(finalRgb, uTintColor.a);
        }
    """

    /**
     * Unlit Shader: Used for sky dome, blob shadows, boost flames, drift sparks, confetti, and dust particles.
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
