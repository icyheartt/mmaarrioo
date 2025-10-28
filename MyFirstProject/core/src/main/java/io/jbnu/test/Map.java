package io.jbnu.test;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;

public class Map {

    private Array<Block> blocks;
    private Array<Pipe> pipes;
    private Flag flag;
    private int lev;
    private Texture blockTexture;
    private Texture flagTexture;
    private Texture pipeDownTexture;
    private Texture pipeUpTexture;
    private boolean underwater;
    public Map(int level) {
        lev = level;
        blocks = new Array<>();
        pipes = new Array<>();
        blockTexture = new Texture("block.png");
        flagTexture = new Texture("flag.png");
        pipeDownTexture = new Texture("pipe.png");
        pipeUpTexture = new Texture("pipedown.png");

        build(level);
    }

    private void build(int level) {

        underwater = false;

        switch (level) {
            case 1: {
                underwater = false;

                for (int x = 0; x <= 2000; x += 128) {
                    blocks.add(new Block(x, 0, blockTexture));
                }
                pipes.add(new Pipe(900, 128, pipeDownTexture, Pipe.Orientation.DOWN));
                flag = null;
                break;
            }
            case 2: {
                underwater = true;

                for (int x = 0; x <= 1600; x += 128) {
                    blocks.add(new Block(x, -256, blockTexture));
                }
                pipes.add(new Pipe(1700, 128, pipeUpTexture, Pipe.Orientation.UP));
                flag = null;
                break;
            }
            case 3: {
                underwater = false;
                for (int x = 0; x <= 1000; x += 128) {
                    blocks.add(new Block(x, 0, blockTexture));
                }
                flag = new Flag(900, 0, flagTexture);
                break;
            }
            default: {
                for (int x = 0; x <= 1000; x += 128) {
                    blocks.add(new Block(x, 0, flagTexture));
                }
                flag = new Flag(900, 0, flagTexture);
                break;
            }
        }
    }

    public Array<Block> getBlocks() {
        return blocks;
    }
    public Array<Pipe> getPipes() { return pipes; }
    public Flag getFlag() { return flag; }
    public boolean isUnderwater() { return underwater; }
}
