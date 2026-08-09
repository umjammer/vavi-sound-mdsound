
package test.soundManager;

abstract class BaseSender extends BaseMakerSender {
    int counter = 0;
    int dev = 0;
    int typ = 0;
    int adr = 0;
    int val = 0;
    Object[] ex = null;
    int ringBufferSize;
}
