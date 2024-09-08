
package test.soundManager;

import vavi.util.Debug;


/**
 * データ生成器
 * ミュージックドライバーを駆動させ、生成するデータをDataSenderに送る
 */
public class DataMaker extends BaseMakerSender {
    private final DriverAction actionOfDriver;
    private boolean pause = false;

    public DataMaker(DriverAction actionOfDriver) {
        action = this::main;
        this.actionOfDriver = actionOfDriver;
    }

    private void main() {
        try {
            while (true) {

                pause = false;

                while (true) {
                    Thread.sleep(100);
                    if (getStart()) {
                        break;
                    }
                }

                synchronized (lockObj) {
                    isRunning = true;
                }

                if (actionOfDriver != null) if (actionOfDriver.init != null) actionOfDriver.init.run();

                while (true) {
                    if (!getStart())
                        break;

                    if (pause) {
                        if (parent.getDataSenderBufferSize() >= DATA_SEQUENCE_FREQUENCE / 2) {
                            Thread.yield();
                            continue;
                        }

                        pause = false;
                    }

                    Thread.yield();
                    if (actionOfDriver != null) if (actionOfDriver.main != null) actionOfDriver.main.run();

                    if (parent.getDataSenderBufferSize() >= DATA_SEQUENCE_FREQUENCE) {
                        pause = true;
                    }
                }

                if (actionOfDriver != null) if (actionOfDriver.main != null) actionOfDriver.final_.run();

                synchronized (lockObj) {
                    isRunning = false;
                }

            }
        } catch (Exception e) {
Debug.println(e);
            synchronized (lockObj) {
                isRunning = false;
                start = false;
            }
        }
    }
}
