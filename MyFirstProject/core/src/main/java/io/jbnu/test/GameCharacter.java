package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class GameCharacter {

    // === 상태(위치/속도/접지) ===
    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    public boolean isGrounded = false;

    // === 이동/점프 파라미터 (GameWorld가 읽어서 사용) ===
    public float moveSpeed    = 300f;  // 지상에서 좌/우 목표 속도
    public float jumpVelocity = 650f;  // 지상 점프 초기속도
    public float swimThrust   = 420f;  // 수중 상승 가속(월드가 dt 곱해 적용)

    // === [추가] 수중 모드/파라미터 ===
    private boolean underwater = false;     // 레벨2에서 true
    public  float  waterGravity = -150f;    // 물속 중력(약하게)
    public  float  swimSpeed    = 120f;     // 상/하 수영 속도 (고정치)
    public  float  waterDrag    = 0.90f;    // 물 저항(감쇠)
    public  float  maxSwimVy    = 220f;     // 수직 속도 제한

    // === 입력 '의도' (GameWorld가 소비) ===
    // -1: left, 0: stop, 1: right
    private int desiredDir = 0;
    private boolean jumpRequested   = false;
    private boolean swimUpRequested = false;
    private int desiredSwimY = 0;

    // === 렌더링 관련(월드가 애니메이션 상태만 결정) ===
    public enum Anim { IDLE, RUN, JUMP, SWIM }
    public Anim anim = Anim.IDLE;
    public boolean facingLeft = false;
    public final Sprite sprite;
    private boolean isDead;
    private float startX, startY;

    public GameCharacter(Vector2 startPos, Texture texture) {
        this.position.set(startPos);
        this.sprite = new Sprite(texture);
        syncSpriteToPosition();
        this.isDead = false;
        startX = startPos.x;
        startY = startPos.y;
    }

    // === 의도 입력 메서드 (이동만 담당) ===
    public void onPlayerLeft()     { desiredDir = -1; }
    public void onPlayerRight()    { desiredDir =  1; }
    public void onPlayerStop()     { desiredDir =  0; }
    public void onPlayerJump()     { jumpRequested = true; }
    public void onPlayerSwimUp()   { swimUpRequested = true; }
    public void onPlayerSwimUpHold()   { desiredSwimY =  1; }
    public void onPlayerSwimDownHold() { desiredSwimY = -1; }
    public void onPlayerSwimStop()     { desiredSwimY =  0; }

    // === 월드가 읽어가는 getter (consume 패턴) ===
    public int getDesiredDir() { return desiredDir; }

    public boolean consumeJumpRequested() {
        boolean j = jumpRequested;
        jumpRequested = false;
        return j;
    }

    public boolean consumeSwimUpRequested() {
        boolean s = swimUpRequested;
        swimUpRequested = false;
        return s;
    }

    public int  getDesiredSwimY() { return desiredSwimY; }
    public boolean isUnderwater() { return underwater; }
    public void setUnderwater(boolean value) { underwater = value; }
    public float getWaterGravity() { return waterGravity; }
    public float getSwimSpeed()    { return swimSpeed; }
    public float getWaterDrag()    { return waterDrag; }
    public float getMaxSwimVy()    { return maxSwimVy; }
    public Rectangle getBounds() {
        Rectangle bound = new Rectangle(position.x, position.y, sprite.getWidth(), sprite.getHeight());
        return bound;
    }

    public void kill() {
        isDead = true;
        // 죽는 애니메이션 등 추가 가능
    }

    public void respawnAtStart() {
        this.position.set(startX, startY);
        this.isDead = false;
    }

    public void setStartPosition(float x, float y) {
        this.startX = x;
        this.startY = y;
    }

    // === 스프라이트 위치 동기화 ===
    public void syncSpriteToPosition() {
        sprite.setPosition(position.x, position.y);
    }

    public float getWidth()  { return sprite.getWidth(); }
    public float getHeight() { return sprite.getHeight(); }
}
