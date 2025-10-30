package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.files.FileHandle;

public class Map {
    private Array<Block> groundBlocks;
    private Array<Rectangle> deathZones;
    private Array<Object> entities;
    private Array<Pipe> pipeTriggers;
    private Flag flag;

    private int level;
    private Texture blockTexture;
    private Texture pipeUpTexture;
    private Texture pipeDownTexture;
    private Texture flagTexture;

    public Map(int level) {
        this.level = level;
        groundBlocks = new Array<>();
        deathZones = new Array<>();
        entities = new Array<>();
        pipeTriggers = new Array<>();
        flag = null;

        blockTexture = new Texture(Gdx.files.internal("block.png"));
        pipeUpTexture = new Texture(Gdx.files.internal("pipe.png"));
        pipeDownTexture = new Texture(Gdx.files.internal("pipedown.png"));
        flagTexture = new Texture(Gdx.files.internal("flag.png"));

        String mapPath = getMapPath(level);
        loadFromJson(mapPath);
    }

    private String getMapPath(int level) {
        // 내부적으로 MyFirstProject/assets/maps/levelX.json 사용
        switch (level) {
            case 1: return "maps/level1.json";
            case 2: return "maps/level2.json";
            case 3: return "maps/level3.json";
            default: return "maps/level1.json"; // 기본값
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromJson(String mapPath) {
        try {
            FileHandle file = Gdx.files.internal(mapPath);
            Json json = new Json();
            MapData mapData = json.fromJson(MapData.class, file);

            // === Ground blocks ===
            for (MapData.TileDef tile : mapData.ground) {
                for (int x = (int) tile.x; x < tile.x + tile.w; x += 50) {
                    for (int y = (int) tile.y; y < tile.y + tile.h; y += 50) {
                        groundBlocks.add(new Block(x, y, blockTexture));
                    }
                }
            }

            // === Death zones ===
            for (MapData.RectDef rect : mapData.deathZones) {
                deathZones.add(new Rectangle(rect.x, rect.y, rect.w, rect.h));
            }

            // === Pipe triggers ===
            if (mapData.pipes != null) {
                for (MapData.PipeDef p : mapData.pipes) {
                    Pipe.Orientation orientation = Pipe.Orientation.DOWN;
                    Texture selectedTexture = pipeDownTexture;
                    if ("up".equalsIgnoreCase(p.orientation)) {
                        orientation = Pipe.Orientation.UP;
                        selectedTexture = pipeUpTexture;
                    }
                    pipeTriggers.add(new Pipe(p.x, p.y, selectedTexture, orientation));
                }
            }

            // === Flags ===
            if (mapData.flag != null) {
                flag = new Flag(mapData.flag.x, mapData.flag.y, flagTexture);
            }

        } catch (Exception e) {
            Gdx.app.error("Map", "Failed to load " + mapPath, e);
        }
    }

    public Array<Block> getBlocks() { return groundBlocks; }

    public Array<Pipe> getPipes() { return pipeTriggers; }

    public Flag getFlag() { return flag; }

    public boolean isUnderwater() {
        if (level == 2) return true;
        else return false;
    }


    // ==========================
    // Inner data class for JSON
    // ==========================
    public static class MapData {
        public Array<TileDef> ground;
        public Array<RectDef> deathZones;
        public Array<EntityDef> entities;
        public Array<PipeDef> pipes;
        public FlagDef flag;

        public static class TileDef {
            public float x, y, w, h;
        }

        public static class RectDef {
            public float x, y, w, h;
        }

        public static class PipeDef {
            public float x, y;
            public String orientation; // up/down/left/right
        }

        public static class EntityDef {
            public String kind;
            public float x, y, w, h;
        }

        public static class FlagDef {
            public float x, y;
        }
    }
}
