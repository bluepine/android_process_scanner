import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;

import java.lang.Thread;

public class APScan {
	static AndroidDebugBridge adb;

	static private boolean wait_for_connection() {
		int i;
		for (i = 0; i < 5; i++) {
			if (adb.isConnected()) {
				return true;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
	}

	public static void main(String[] args) {
		int i, client_number;
		String adb_path = "/home/song/android_sdk/android-sdk-linux_86/platform-tools/adb";
		IDevice device_array[];
		Client cll[];
		ClientData cd;
		Log.d("APScan", "Initializing");
		AndroidDebugBridge.init(true);
		System.out.println("AndroidDebugBridge initialized\n");
		// adb = AndroidDebugBridge.createBridge(adb_path, true);
		adb = AndroidDebugBridge.createBridge();
		if (adb == null) {
			System.out.println("createBridge() failed\n");
		} else {
			System.out.println("createBridge() succeeded\n");

			if (wait_for_connection()) {
				client_number = -1;
				System.out.println("adb is connected\n");
				device_array = adb.getDevices();
				for (IDevice d : device_array) {
					System.out.println(d.toString());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					//
					//					for (i = 0; i < 1000; i++) {
					//						cll = d.getClients();
					//						if (cll.length > client_number) {
					//							client_number = cll.length; 
					//							i--;
					//							try {
					//								Thread.sleep(100);
					//							} catch (InterruptedException e) {
					//								// TODO Auto-generated catch block
					//								e.printStackTrace();
					//							}
					//							continue;
					//						}
					//					}
					cll = d.getClients();
					for (Client c : cll) {
						cd = c.getClientData();
						System.out.println("client:" + c.toString());
						System.out.println("client:"
								+ cd.getClientDescription());
					}

				}
			} else {
				System.out.println("adb is not connected\n");
			}
		}
		// AndroidDebugBridge.disconnectBridge();
		AndroidDebugBridge.terminate();
		System.out.println("AndroidDebugBridge terminated\n");
	}
}
