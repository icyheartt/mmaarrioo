package io.jbnu.test;

public class InputState {
    public boolean left;
    public boolean right;
    public boolean up;
    public boolean down;
    public boolean jump;
    public boolean pause;

    public void clear() {
        left = right = up = down = jump = pause = false;
    }
}
