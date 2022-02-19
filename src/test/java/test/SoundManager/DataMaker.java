
package test.SoundManager;

/// <summary>
/// データ生成器
/// ミュージックドライバーを駆動させ、生成するデータをDataSenderに送る
/// </summary>
public class DataMaker extends BaseMakerSender {
    private final DriverAction ActionOfDriver;
    private boolean pause = false;

    public DataMaker(DriverAction ActionOfDriver) {
        action = this::Main;
        this.ActionOfDriver = ActionOfDriver;
    }

    private void Main() {
        try {
            while (true) {

                pause = false;

                while (true) {
                    Thread.sleep(100);
                    if (GetStart()) {
                        break;
                    }
                }

                synchronized (lockObj) {
                    isRunning = true;
                }

                ActionOfDriver.Init.run();

                while (true) {
                    if (!GetStart())
                        break;

                    if (pause) {
                        if (parent.GetDataSenderBufferSize() >= DATA_SEQUENCE_FREQUENCE / 2) {
                            Thread.yield();
                            continue;
                        }

                        pause = false;
                    }

                    Thread.yield();
                    ActionOfDriver.Main.run();

                    if (parent.GetDataSenderBufferSize() >= DATA_SEQUENCE_FREQUENCE) {
                        pause = true;
                    }
                }

                ActionOfDriver.Final.run();

                synchronized (lockObj) {
                    isRunning = false;
                }

            }
        } catch (Exception e) {
            synchronized (lockObj) {
                isRunning = false;
                Start = false;
            }
        }
    }
}
