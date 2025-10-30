package io.jbnu.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode;
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

    // 화면 비율
    private static final float VIRTUAL_WIDTH = 1280f;
    private static final float VIRTUAL_HEIGHT = 720f;

    // 애니메이션 관련
    private Animation<TextureRegion> walkAnim;
    private Animation<TextureRegion> swimAnim;
    private float stateTime = 0f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        viewport.apply();
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
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        stateTime += delta;

        // 입력 수집
        pollInput();

        if (input.pause) togglePause();
        if (state == GameState.RUNNING) {
            world.update(delta, input);
        }

        cameraFollow(world);
        camera.update();

        ScreenUtils.clear(0f, 0f, 0f, 1f);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // --- 월드 그리기 ---
        if (world.getBlocks() != null)
            for (Block b : world.getBlocks()) b.draw(batch);
        if (world.getPipes() != null)
            for (Pipe p : world.getPipes()) p.draw(batch);
        if (world.getFlag() != null)
            world.getFlag().draw(batch);

        // --- 플레이어 애니메이션 선택 ---
        GameCharacter player = world.getPlayer();
        TextureRegion currentFrame;
        if (world.isUnderwater()) {
            currentFrame = swimAnim.getKeyFrame(stateTime);
        } else {
            if (player.anim == GameCharacter.Anim.RUN)
                currentFrame = walkAnim.getKeyFrame(stateTime);
            else if (player.anim == GameCharacter.Anim.JUMP)
                currentFrame = walkAnim.getKeyFrame(0); // 점프 시 첫 프레임 유지
            else
                currentFrame = walkAnim.getKeyFrame(0); // IDLE은 첫 프레임
        }

        float w = currentFrame.getRegionWidth();
        float h = currentFrame.getRegionHeight();
        float drawX = player.position.x;
        float drawY = player.position.y;

        if (player.facingLeft) {
            // 왼쪽을 바라볼 때: 기준점을 오른쪽 가장자리로 옮기고 음수 폭으로 그림
            // (그렇지 않으면 왼쪽으로 한 프레임 폭만큼 밀리는 느낌이 납니다)
            drawX += w;
            batch.draw(currentFrame, drawX, drawY, -w, h);
        } else {
            batch.draw(currentFrame, drawX, drawY,  w, h);
        }

        // --- HUD ---
        drawHud();

        if (state == GameState.PAUSED)
            font.draw(batch, "[PAUSED] Press P to Resume",
                camera.position.x - 120, camera.position.y + VIRTUAL_HEIGHT * 0.35f);

        float a = world.getTransitionAlpha();
        if (a > 0f && fadeOverlay != null) {
            batch.setColor(1f, 1f, 1f, a);
            batch.draw(fadeOverlay, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            batch.setColor(1f, 1f, 1f, 1f);
        }

        batch.end();

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
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        if (fadeOverlay != null) fadeOverlay.dispose();
        if (world != null) world.disposeFadeSfx();
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

}
