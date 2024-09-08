
package test.soundManager;

public abstract class BaseSender extends BaseMakerSender {
    protected int counter = 0;
    protected int dev = 0;
    protected int typ = 0;
    protected int adr = 0;
    protected int val = 0;
    protected Object[] ex = null;
    protected int ringBufferSize;
}
