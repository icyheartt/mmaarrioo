package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * 입력은 Main 등에서 수집해 InputState로 전달.
 * 본 클래스는:
 *  - 캐릭터 이동 '의도'만 GameCharacter에 전달
 *  - 물리(중력/마찰/수중 드래그/점프·수영), 충돌, 파이프, 레벨 전환, 애니메이션 상태 결정까지 담당
 */
public class GameWorld {

    // === 레벨 타입 ===
    public enum LevelType { GROUND, UNDERWATER }

    // === 월드 상수 ===
    public static final float WORLD_GRAVITY = -9.8f * 200f; // px/s^2
    public static final float FLOOR_LEVEL = 0f;

    // === 수중 보정 상수 ===
    private static final float WATER_GRAVITY_SCALE = 0.35f; // 중력 약화
    private static final float WATER_DRAG          = 4.0f;  // 속도 감쇠

    // === 구성 요소 ===
    private GameCharacter player;
    private int swimHoldDir = 0; // -1: 아래, 0: 없음, 1: 위
    private static final float SWIM_HOLD_SPEED = 120f;
    private static final float MAX_SWIM_VY     = 220f;
    private Array<Block> blocks;
    private Array<CoinObject> coins;
    private Array<Pipe> pipes;
    private Flag flag;

    private Map currentMap;
    private int level = 1;
    private int score = 0;

    private LevelType levelType = LevelType.GROUND;

    // (간단) 리소스 — 실제 프로젝트에서는 AssetManager로 관리 권장
    private final Texture playerTexture;

    public GameWorld(Texture initialTexture) {
        this.playerTexture = initialTexture;
        loadLevel(level);
    }

    // ---------------------------
    // 레벨 로딩
    // ---------------------------
    private void loadLevel(int lev) {
        currentMap = new Map(lev);

        blocks = currentMap.getBlocks();
        pipes  = currentMap.getPipes();
        flag   = currentMap.getFlag();

        levelType = currentMap.isUnderwater() ? LevelType.UNDERWATER : LevelType.GROUND;

        if (player == null) {
            player = new GameCharacter(new Vector2(128, 256), playerTexture);
        } else {
            // 레벨 시작 시 간단한 리스폰 위치
            player.position.set(128, 256);
            player.velocity.set(0, 0);
            player.isGrounded = false;
            player.syncSpriteToPosition();
        }

        coins = new Array<>(); // 필요 시 맵에 맞춰 채우기
        score = 0;
    }

    // ---------------------------
    // 메인 업데이트
    // ---------------------------
    public void update(float delta, InputState input) {

        // 1) 이동 '의도'만 캐릭터에 전달
        if (input.left ^ input.right) {
            if (input.left)  player.onPlayerLeft();
            if (input.right) player.onPlayerRight();
        } else {
            player.onPlayerStop();
        }

        if (levelType == LevelType.GROUND) {
            if (input.jump) player.onPlayerJump();     // 점프 의도
        } else {
            if (input.up)   player.onPlayerSwimUp();   // 수영 상승 의도
        }

        // 2) 물리(가속/중력/드래그/적분)
        // 수중 상/하 홀드 입력값 저장
        if (levelType == LevelType.UNDERWATER) {
            if (input.up && !input.down) {
                swimHoldDir = 1;
            } else if (input.down && !input.up) {
                swimHoldDir = -1;
            } else {
                swimHoldDir = 0;
            }
        } else {
            swimHoldDir = 0;
        }

        applyPhysics(delta);

        // 3) 블록 충돌
        resolveBlockCollision();

        //죽음 체크
        if (player.position.y <= FLOOR_LEVEL) {
            player.kill();
            player.respawnAtStart();
        }

        // 4) 파이프(레벨 전환)
        handlePipeEnter(input);

        // 5) 깃발 → 다음 레벨
        if (flag != null && playerRect().overlaps(flag.getBound())) {
            goNextLevel();
        }

        // 6) 애니메이션 상태 결정 (프레임 선택/그리기는 렌더 단계에서)
        updateAnimationState();

        player.syncSpriteToPosition();
    }

    // ---------------------------
    // 물리 처리
    // ---------------------------
    private void applyPhysics(float delta) {
        // --- 수평 (의도 기반) ---
        final int dir = player.getDesiredDir();
        if (dir != 0) {
            player.velocity.x = dir * player.moveSpeed;
            player.facingLeft = (dir < 0);
        } else {
            // 정지 의도일 때 감속(지상/수중 공통)
            final float friction = (levelType == LevelType.UNDERWATER) ? 6.0f : 8.0f;
            player.velocity.x -= player.velocity.x * friction * delta;
            // 임계치 스냅
            if (Math.abs(player.velocity.x) < 1f) player.velocity.x = 0f;
        }

        // --- 점프/수영 의도 소비 ---
        if (levelType == LevelType.GROUND) {
            if (player.consumeJumpRequested() && player.isGrounded) {
                player.velocity.y = player.jumpVelocity;
                player.isGrounded = false;
            }
        } else {
            if (player.consumeSwimUpRequested()) {
                player.velocity.y += player.swimThrust * delta;
            }
        }
        // 수중 상/하 홀드 이동 (UP/DOWN 키를 누르고 있는 동안)
        if (levelType == LevelType.UNDERWATER) {
            if (swimHoldDir == 1) {
                player.velocity.y = SWIM_HOLD_SPEED;
            } else if (swimHoldDir == -1) {
                player.velocity.y = -SWIM_HOLD_SPEED;
            }
        }


        // --- 중력/수중 드래그 ---
        float gravity = WORLD_GRAVITY * (levelType == LevelType.UNDERWATER ? WATER_GRAVITY_SCALE : 1f);
        player.velocity.y += gravity * delta;

        if (levelType == LevelType.UNDERWATER) {
            player.velocity.x -= player.velocity.x * WATER_DRAG * delta;
            player.velocity.y -= player.velocity.y * WATER_DRAG * delta;
        }

        // --- 위치 적분 ---
        player.position.x += player.velocity.x * delta;
        player.position.y += player.velocity.y * delta;

        // --- 지상 바닥 클램프 ---
        if (levelType == LevelType.GROUND && player.position.y < FLOOR_LEVEL) {
            player.position.y = FLOOR_LEVEL;
            player.velocity.y = 0f;
            player.isGrounded = true;
        }
    }

    // ---------------------------
    // 충돌 처리 (AABB, 최소침투 해법)
    // ---------------------------
    private void resolveBlockCollision() {
        if (blocks == null || blocks.size == 0) return;

        Rectangle pr = playerRect();

        for (Block b : blocks) {
            Rectangle br = b.getBound();
            if (!pr.overlaps(br)) continue;

            // 침투량 계산
            float prLeft   = pr.x;
            float prRight  = pr.x + pr.width;
            float prBottom = pr.y;
            float prTop    = pr.y + pr.height;

            float brLeft   = br.x;
            float brRight  = br.x + br.width;
            float brBottom = br.y;
            float brTop    = br.y + br.height;

            float overlapX = Math.min(prRight - brLeft, brRight - prLeft);
            float overlapY = Math.min(prTop   - brBottom, brTop   - prBottom);

            // 작은 축으로 분리
            if (overlapX < overlapY) {
                // X축 분리
                if (pr.x < br.x) {
                    // 왼쪽에서 오른쪽으로 박힘
                    player.position.x -= overlapX;
                } else {
                    // 오른쪽에서 왼쪽으로 박힘
                    player.position.x += overlapX;
                }
                player.velocity.x = 0f;
            } else {
                // Y축 분리
                if (pr.y < br.y) {
                    // 아래에서 위로 박음(머리)
                    player.position.y -= overlapY;
                    player.velocity.y = 0f;
                    // 머리 부딪힘은 접지 아님
                } else {
                    // 위에서 아래로 떨어져 착지
                    player.position.y += overlapY;
                    player.velocity.y = 0f;
                    player.isGrounded = true;
                }
            }

            // 위치 반영 후, 충돌 사각형 갱신
            pr.set(player.position.x, player.position.y, pr.width, pr.height);
        }
    }

    // ---------------------------
    // 파이프 진입 (레벨 전환)
    // ---------------------------
    private void handlePipeEnter(InputState input) {
        if (pipes == null || pipes.size == 0) return;

        Rectangle pr = playerRect();
        for (Pipe pipe : pipes) {
            if (!pr.overlaps(pipe.getBounds())) continue;

            if (pipe.getOrientation() == Pipe.Orientation.DOWN && input.up) {
                goNextLevel(); // 1 -> 2
                return;
            }
            if (pipe.getOrientation() == Pipe.Orientation.UP && input.down) {
                goNextLevel(); // 2 -> 3
                return;
            }
        }
    }

    private void goNextLevel() {
        if (coins != null)  coins.clear();
        if (blocks != null) blocks.clear();
        if (pipes != null)  pipes.clear();

        level++;
        loadLevel(level);
    }

    // ---------------------------
    // 애니메이션 상태 결정 (그리기는 렌더 단계에서)
    // ---------------------------
    private void updateAnimationState() {
        if (levelType == LevelType.UNDERWATER) {
            player.anim = (Math.abs(player.velocity.y) > 20f)
                ? GameCharacter.Anim.SWIM
                : GameCharacter.Anim.IDLE;
            return;
        }

        // 지상: 점프/낙하 우선 → 달리기 → 대기
        boolean airborne = !player.isGrounded || Math.abs(player.velocity.y) > 1f;
        if (airborne) {
            player.anim = GameCharacter.Anim.JUMP;
        } else if (Math.abs(player.velocity.x) > 40f) {
            player.anim = GameCharacter.Anim.RUN;
        } else {
            player.anim = GameCharacter.Anim.IDLE;
        }
    }

    // ---------------------------
    // 유틸
    // ---------------------------
    private Rectangle playerRect() {
        return new Rectangle(
            player.position.x,
            player.position.y,
            player.getWidth(),
            player.getHeight()
        );
    }

    // === 외부 접근자 ===
    public GameCharacter getPlayer() { return player; }
    public Array<Block> getBlocks()  { return blocks; }
    public Array<Pipe> getPipes()    { return pipes; }
    public Flag getFlag()            { return flag;  }
    public boolean isUnderwater()    { return levelType == LevelType.UNDERWATER; }
    public int getLevel()            { return level; }
    public int getScore()            { return score; }
}
