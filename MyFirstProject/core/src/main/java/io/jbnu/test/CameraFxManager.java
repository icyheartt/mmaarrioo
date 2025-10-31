package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * 카메라 관련 기능(레벨별 줌, 수중 후처리(FBO+셰이더), 리사이즈/정리)을 전담하는 매니저.
 * - 수중(레벨2)일 때: 카메라 줌 축소 + 굴절/살짝 블러/틴트 후처리
 * - 비수중: 기존 렌더 경로로 자동 폴백
 *
 * 사용법:
 *   camFx = new CameraFxManager();
 *   camFx.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
 *   ...
 *   camFx.setUnderwater(isUnderwater);
 *   camFx.update(dt, camera);
 *   if (camFx.isActive()) {
 *       camFx.beginScene();         // FBO로 렌더 시작
 *       // batch.begin(); 월드/플레이어 그리기 ... batch.end();
 *       camFx.endSceneAndPost(camera, batch); // 셰이더 후처리 출력
 *   } else {
 *       // 기존처럼 바로 그리기
 *   }
 *   // HUD/텍스트/페이드 등은 후처리 이후에 batch로 다시 그리기 권장
 */
public class CameraFxManager {

    // 설정값
    private static final float UNDERWATER_ZOOM = 1.25f;

    // 상태
    private boolean underwater = false;
    private float time = 0f;

    // 리소스
    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private ShaderProgram shader;

    public CameraFxManager() {
        initShader();
    }

    public void setUnderwater(boolean on) { this.underwater = on; }
    public boolean isUnderwater() { return underwater; }

    /** 현재 프레임에서 후처리 경로를 쓸지 여부 (셰이더/FBO 준비 상태 포함) */
    public boolean isActive() {
        return underwater && shader != null && fbo != null;
    }

    /** 카메라 갱신(수중 줌 반영) 및 시간 진행 */
    public void update(float dt, OrthographicCamera camera) {
        time += dt;
        camera.zoom = underwater ? UNDERWATER_ZOOM : 1.0f;
        camera.update();
    }

    /** 화면 사이즈 변경 시 FBO 재생성 */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        Texture tex = fbo.getColorBufferTexture();
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        fboRegion = new TextureRegion(tex);
        fboRegion.flip(false, true); // FBO의 상하 반전 보정
    }

    /** FBO에 씬을 그리기 시작 (begin) */
    public void beginScene() {
        if (fbo == null) return;
        fbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /** FBO 종료하고 수중 셰이더로 후처리 출력 */
    public void endSceneAndPost(OrthographicCamera camera, SpriteBatch batch) {
        if (fbo == null) return;
        fbo.end();

        if (shader == null) {
            // 셰이더가 없으면 그냥 FBO 텍스처를 덮어 그림(거의 폴백 상황)
            batch.setShader(null);
            batch.begin();
            drawFboToCameraView(camera, batch);
            batch.end();
            return;
        }

        batch.setShader(shader);
        batch.begin();
        shader.setUniformf("u_resolution", (float)Gdx.graphics.getWidth(), (float)Gdx.graphics.getHeight());
        drawFboToCameraView(camera, batch);
        batch.end();
        batch.setShader(null);

    }

    /** 카메라 뷰포트 영역에 FBO 텍스처를 그대로 덮어 그림 */
    private void drawFboToCameraView(OrthographicCamera camera, SpriteBatch batch) {
        float left   = camera.position.x - camera.viewportWidth  / 2f;
        float bottom = camera.position.y - camera.viewportHeight / 2f;
        batch.draw(fboRegion, left, bottom, camera.viewportWidth, camera.viewportHeight);
    }

    /** 리소스 정리 */
    public void dispose() {
        if (fbo != null) { fbo.dispose(); fbo = null; }
        if (shader != null) { shader.dispose(); shader = null; }
    }

    // ---------- 셰이더 초기화 ----------
    private void initShader() {
        ShaderProgram.pedantic = false;

        String vert =
            "attribute vec4 a_position;\n" +
                "attribute vec2 a_texCoord0;\n" +
                "uniform mat4 u_projTrans;\n" +
                "varying vec2 v_texCoord;\n" +
                "void main(){\n" +
                "  v_texCoord = a_texCoord0;\n" +
                "  gl_Position = u_projTrans * a_position;\n" +
                "}\n";

        String frag =
            "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
                "varying vec2 v_texCoord;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform vec2 u_resolution;\n" +
                "void main(){\n" +
                "  vec2 texel = 1.0 / u_resolution;\n" +
                "  vec4 c = texture2D(u_texture, v_texCoord) * 4.0;\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2( texel.x, 0.0));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2(-texel.x, 0.0));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2(0.0,  texel.y));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2(0.0, -texel.y));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2( texel.x,  texel.y));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2(-texel.x,  texel.y));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2( texel.x, -texel.y));\n" +
                "  c += texture2D(u_texture, v_texCoord + vec2(-texel.x, -texel.y));\n" +
                "  gl_FragColor = c / 13.0; // 약한 9탭 박스블러\n" +
                "}\n";

        shader = new ShaderProgram(vert, frag);
        if (!shader.isCompiled()) {
            Gdx.app.error("SHADER", shader.getLog());
            shader = null; // 폴백 허용
        }
    }
}
