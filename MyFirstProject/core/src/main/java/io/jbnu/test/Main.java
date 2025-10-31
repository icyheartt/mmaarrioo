package io.jbnu.test;

import static com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable.draw;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class Main extends ApplicationAdapter {

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private BitmapFont font;
    private Texture fadeOverlay; // 1x1 black
    private GameWorld world;
    private final InputState input = new InputState();

    private enum GameState { RUNNING, PAUSED }
    private GameState state = GameState.RUNNING;
    private CameraFxManager camFx;
    // 화면 비율
    private static final float VIRTUAL_WIDTH = 1280f;
    private static final float VIRTUAL_HEIGHT = 720f;

    // 애니메이션 관련
    private Animation<TextureRegion> walkAnim;
    private Animation<TextureRegion> swimAnim;
    private float stateTime = 0f;
    // --- Underwater post-process ---
    private FrameBuffer waterFbo;
    private ShaderProgram waterShader;
    private TextureRegion waterRegion; // FBO를 뒤집은 텍스처 영역
    private float waterTime = 0f;
    private static final float UNDERWATER_ZOOM = 1.25f; // 시야 살짝 좁히기

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        viewport.apply();
        camFx = new CameraFxManager();
        camFx.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT / 2, 0);
        camera.update();
        font = new BitmapFont();

        walkAnim = GifDecoder.loadGIFAnimation(PlayMode.LOOP, Gdx.files.internal("mario.gif").readBytes());
        swimAnim = GifDecoder.loadGIFAnimation(PlayMode.LOOP, Gdx.files.internal("swim.gif").readBytes());

        if (walkAnim == null)  walkAnim = makeSingleFrameAnim(64, 64); // 흰색 64x64
        if (swimAnim == null)  swimAnim = walkAnim;

        TextureRegion first = walkAnim.getKeyFrame(0f);
        Texture initial = first.getTexture();

        world = new GameWorld(initial);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0,0,0,1);
        pm.fill();
        fadeOverlay = new Texture(pm);
        pm.dispose();

        initUnderwaterShader();
        initUnderwaterFbo(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        stateTime += delta;

        // 1) 입력 수집 + 일시정지 토글
        pollInput();
        if (input.pause) togglePause();

        // 2) 업데이트 (RUNNING일 때만)
        if (state == GameState.RUNNING) {
            world.update(delta, input);
        }

        // 3) 카메라: 플레이어 중심 보간 추적
        cameraFollow(world);

        // 4) 레벨2(수중) 여부
        boolean underwater = false;
        try { underwater = world.isUnderwater(); }
        catch (Throwable t) { try { underwater = (world.getLevel() == 2); } catch (Throwable ignore) {} }

        // 5) 카메라 FX에 수중 여부 전달 + 내부 업데이트
        if (camFx != null) {
            camFx.setUnderwater(underwater);
            camFx.update(delta, camera); // (내부에서 수중이면 고정 줌을 적용함)
        }

        // ★ 6) 우리가 원하는 "가로 100px 시야"를 camFx.update() 직후에 '덮어쓰기'
        if (underwater) {
            float targetWidthWorld = 100f;                      // 원하는 가로 시야(픽셀 단위 가정)
            float targetZoom = targetWidthWorld / camera.viewportWidth;
            // 부드럽게 보간(즉시 적용 원하면 camera.zoom = targetZoom;)
            camera.zoom += (targetZoom - camera.zoom) * 0.35f;
        } else {
            // 수중 아니면 원래 배율로 부드럽게 복귀
            camera.zoom += (1.0f - camera.zoom) * 0.35f;
        }
        camera.update();

        // 7) 화면 클리어 & 공통 설정
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        batch.setProjectionMatrix(camera.combined);

        final boolean useFx = (underwater && camFx != null && camFx.isActive());

        if (useFx) {
            // ===== 수중 경로: 씬→FBO, FBO→'약한 블러'만 적용, HUD/페이드는 또렷하게 =====
            camFx.beginScene();

            batch.setShader(null);
            batch.begin();

            // 월드(블록/파이프/깃발)
            if (world.getBlocks() != null) for (Block b : world.getBlocks()) b.draw(batch);
            if (world.getPipes()  != null) for (Pipe  p : world.getPipes())  p.draw(batch);
            if (world.getFlag()   != null) world.getFlag().draw(batch);

            // 플레이어: 수중 GIF 프레임 (루프 보장)
            GameCharacter player = world.getPlayer();
            Animation<TextureRegion> safeWalk = (walkAnim != null) ? walkAnim : makeSingleFrameAnim(64,64);
            Animation<TextureRegion> safeSwim = (swimAnim != null) ? swimAnim : safeWalk;
            TextureRegion currentFrame = safeSwim.getKeyFrame(stateTime, true);

            float w = currentFrame.getRegionWidth();
            float h = currentFrame.getRegionHeight();
            float drawX = player.position.x;
            float drawY = player.position.y;
            if (player.facingLeft) { drawX += w; batch.draw(currentFrame, drawX, drawY, -w, h); }
            else                   {              batch.draw(currentFrame, drawX, drawY,  w, h); }

            batch.end();
            camFx.endSceneAndPost(camera, batch); // 블러만 적용해서 화면에 투사

            // HUD/PAUSE/페이드 (블러 없이 또렷)
            batch.setShader(null);
            batch.begin();

            drawHud();
            if (state == GameState.PAUSED) {
                font.draw(batch, "[PAUSED] Press P to Resume",
                    camera.position.x - 120, camera.position.y + VIRTUAL_HEIGHT * 0.35f);
            }

            float a = world.getTransitionAlpha();
            if (a > 0f && fadeOverlay != null) {
                batch.setColor(1f, 1f, 1f, a);
                batch.draw(fadeOverlay, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                batch.setColor(1f, 1f, 1f, 1f);
            }

            batch.end();

        } else {
            // ===== 일반 경로 =====
            batch.setShader(null);
            batch.begin();

            if (world.getBlocks() != null) for (Block b : world.getBlocks()) b.draw(batch);
            if (world.getPipes()  != null) for (Pipe  p : world.getPipes())  p.draw(batch);
            if (world.getFlag()   != null) world.getFlag().draw(batch);

            // 플레이어: 지상/점프/대기 프레임 선택 (루프 보장)
            GameCharacter player = world.getPlayer();
            Animation<TextureRegion> safeWalk = (walkAnim != null) ? walkAnim : makeSingleFrameAnim(64,64);
            Animation<TextureRegion> safeSwim = (swimAnim != null) ? swimAnim : safeWalk;
            TextureRegion currentFrame;
            if (underwater) {
                currentFrame = safeSwim.getKeyFrame(stateTime, true);
            } else {
                if (player.anim == GameCharacter.Anim.RUN)
                    currentFrame = safeWalk.getKeyFrame(stateTime, true);
                else if (player.anim == GameCharacter.Anim.JUMP)
                    currentFrame = safeWalk.getKeyFrame(0f, true);
                else
                    currentFrame = safeWalk.getKeyFrame(0f, true);
            }

            float w = currentFrame.getRegionWidth();
            float h = currentFrame.getRegionHeight();
            float drawX = player.position.x;
            float drawY = player.position.y;
            if (player.facingLeft) { drawX += w; batch.draw(currentFrame, drawX, drawY, -w, h); }
            else                   {              batch.draw(currentFrame, drawX, drawY,  w, h); }

            // HUD/PAUSE
            drawHud();
            if (state == GameState.PAUSED) {
                font.draw(batch, "[PAUSED] Press P to Resume",
                    camera.position.x - 120, camera.position.y + VIRTUAL_HEIGHT * 0.35f);
            }

            // 페이드
            float a = world.getTransitionAlpha();
            if (a > 0f && fadeOverlay != null) {
                batch.setColor(1f, 1f, 1f, a);
                batch.draw(fadeOverlay, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                batch.setColor(1f, 1f, 1f, 1f);
            }

            batch.end();
        }

        // 8) 엣지 입력 리셋(이전 버전 흐름 유지)
        input.jump = false;
        input.pause = false;
    }

    private void pollInput() {
        input.left  = Gdx.input.isKeyPressed(Input.Keys.LEFT)  || Gdx.input.isKeyPressed(Input.Keys.A);
        input.right = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        input.up    = Gdx.input.isKeyPressed(Input.Keys.UP)    || Gdx.input.isKeyPressed(Input.Keys.W);
        input.down  = Gdx.input.isKeyPressed(Input.Keys.DOWN)  || Gdx.input.isKeyPressed(Input.Keys.S);
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) input.jump = true;
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) input.pause = true;
    }

    private void togglePause() {
        state = (state == GameState.RUNNING) ? GameState.PAUSED : GameState.RUNNING;
    }

    private void cameraFollow(GameWorld world) {
        if (world == null || world.getPlayer() == null) return;
        float targetX = world.getPlayer().position.x + world.getPlayer().getWidth() * 0.5f;
        float targetY = world.getPlayer().position.y + world.getPlayer().getHeight() * 0.5f;
        float lerp = 0.1f;
        camera.position.x += (targetX - camera.position.x) * lerp;
        camera.position.y += (targetY - camera.position.y) * lerp;
    }

    private void drawHud() {
        String lvType = world.isUnderwater() ? "UNDERWATER" : "GROUND";
        String text1 = String.format("LEVEL: %d | TYPE: %s | SCORE: %d",
            world.getLevel(), lvType, world.getScore());
        font.draw(batch, text1, camera.position.x - 600, camera.position.y + 300);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        initUnderwaterFbo(width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        if (fadeOverlay != null) fadeOverlay.dispose();
        if (world != null) world.disposeFadeSfx();
        if (waterFbo != null) waterFbo.dispose();
        if (waterShader != null) waterShader.dispose();
    }

    private Animation<TextureRegion> safeLoadGif(String path) {
        // 파일 존재 진단
        boolean exists = Gdx.files.internal(path).exists();
        Gdx.app.log("ASSET", path + " exists=" + exists + " path=" + Gdx.files.internal(path).path());
        if (!exists) return null;

        try {
            return GifDecoder.loadGIFAnimation(Animation.PlayMode.LOOP, Gdx.files.internal(path).readBytes());
        } catch (Throwable t) {
            Gdx.app.error("ASSET", "Failed to load gif: " + path, t);
            return null;
        }
    }

    private Animation<TextureRegion> makeSingleFrameAnim(int w, int h) {
        Pixmap px = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        px.setColor(1,1,1,1);
        px.fill();
        Texture tex = new Texture(px);
        px.dispose();
        TextureRegion tr = new TextureRegion(tex);
        Animation<TextureRegion> a = new Animation<TextureRegion>(0.2f, tr); // 더미 프레임
        a.setPlayMode(Animation.PlayMode.LOOP);
        return a;
    }

    private void initUnderwaterShader() {
        // LibGDX: 셰이더 디버그 로그 허용
        ShaderProgram.pedantic = false;

        // 간단한 스크린 패스용 정점 셰이더
        String vert =
            "attribute vec4 a_position;\n" +
                "attribute vec2 a_texCoord0;\n" +
                "uniform mat4 u_projTrans;\n" +
                "varying vec2 v_texCoord;\n" +
                "void main(){\n" +
                "  v_texCoord = a_texCoord0;\n" +
                "  gl_Position = u_projTrans * a_position;\n" +
                "}\n";

        // 수중 굴절 + 약한 박스블러 + 푸른 틴트 + 약한 카스틱
        String frag =
            "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "varying vec2 v_texCoord;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform float u_time;\n" +
                "uniform vec2 u_resolution;\n" +
                "vec2 wobble(vec2 uv){\n" +
                "  float f = 10.0;\n" +
                "  float a = 0.0035;\n" +
                "  uv.y += sin((uv.x + u_time) * f) * a;\n" +
                "  uv.x += cos((uv.y + u_time * 0.7) * f) * a;\n" +
                "  return uv;\n" +
                "}\n" +
                "vec4 blur(sampler2D t, vec2 uv){\n" +
                "  vec2 texel = 1.0 / u_resolution;\n" +
                "  vec4 c = texture2D(t, uv) * 4.0;\n" +
                "  c += texture2D(t, uv + vec2( texel.x, 0.0));\n" +
                "  c += texture2D(t, uv + vec2(-texel.x, 0.0));\n" +
                "  c += texture2D(t, uv + vec2(0.0,  texel.y));\n" +
                "  c += texture2D(t, uv + vec2(0.0, -texel.y));\n" +
                "  c += texture2D(t, uv + vec2( texel.x,  texel.y));\n" +
                "  c += texture2D(t, uv + vec2(-texel.x,  texel.y));\n" +
                "  c += texture2D(t, uv + vec2( texel.x, -texel.y));\n" +
                "  c += texture2D(t, uv + vec2(-texel.x, -texel.y));\n" +
                "  return c / 13.0;\n" +
                "}\n" +
                "void main(){\n" +
                "  vec2 uv = v_texCoord;\n" +
                "  vec2 wuv = wobble(uv);\n" +
                "  vec4 scene = blur(u_texture, wuv);\n" +
                "  // 푸른 틴트 & 비네팅\n" +
                "  vec3 tint = vec3(0.85, 0.95, 1.0);\n" +
                "  scene.rgb = mix(scene.rgb, scene.rgb * tint, 0.25);\n" +
                "  float d = distance(uv, vec2(0.5));\n" +
                "  float vig = smoothstep(0.9, 0.25, 1.0 - d);\n" +
                "  scene.rgb *= vig;\n" +
                "  // 약한 카스틱 하이라이트\n" +
                "  float ca = 0.05 * sin((uv.y*40.0 + u_time*2.0)) * cos((uv.x*30.0 - u_time*1.5));\n" +
                "  scene.rgb += ca;\n" +
                "  gl_FragColor = scene;\n" +
                "}\n";

        waterShader = new ShaderProgram(vert, frag);
        if (!waterShader.isCompiled()) {
            Gdx.app.error("","SHADER\", waterShader.getLog()");
                // 셰이더 실패 시에도 게임이 돌아가도록 null 허용
                waterShader = null;
        }
    }

    private void initUnderwaterFbo(int width, int height) {
        if (waterFbo != null) waterFbo.dispose();
        waterFbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        // FBO 텍스처를 Y-플립한 TextureRegion으로 잡아두기
        Texture fboTex = waterFbo.getColorBufferTexture();
        fboTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        waterRegion = new TextureRegion(fboTex);
        waterRegion.flip(false, true); // LibGDX FBO는 상하 반전되어 있음
    }

}
