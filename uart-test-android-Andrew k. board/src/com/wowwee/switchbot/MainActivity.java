package com.wowwee.switchbot;

import java.io.IOException;
import java.util.ArrayList;

import com.wowwee.drivepath.SBCommHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shenetics.sheai.SHE;
import com.wowwee.drivepath.DPNode;
import com.wowwee.switchbot.SBRobot.DeviceType;
import com.wowwee.switchbot.SBRobot.RobotEvent;
import com.wowwee.switchbot.SBRobot.RobotListener;
import com.wowwee.telepresence.PushServer;
import com.wowwee.telepresence.PushServerListener;
import com.wowwee.util.AdbUtils;
import com.wowwee.util.SBProtocol;
import com.wowwee.util.Utils;
import com.wowwee.websocket_server.CommServer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	// final String TAG = getClass().getSimpleName();
	final String TAG = "SwitchBot";
	private SBRobot mSwitchBotMcu, mXyzMcu;
	private PushServer mLsClient = null;
	// private final boolean IS_REAL_DEVICE = false; // set this flag to false
	// to use the virtual device
	private SHE mAI = null;
	private Context context;
	private CommServer mCommServer;
	private SBCommHandler mCommHandler = null;
	public static final int SB_VIRTUAL_DEVICE = 0;
	public static final int SB_REAL_DEVICE = 1;
	public static final int SB_REAL_DEVICE_MINI = 2;
	public static final int SB_Q410_BOARD = 3;


//	 public static final int SELECTED_DEVICE = SB_VIRTUAL_DEVICE;   // android board only 
//	 public static final int SELECTED_DEVICE = SB_REAL_DEVICE_MINI; // android + arduino 
//	 public static final int SELECTED_DEVICE = SB_REAL_DEVICE;      // android (ODROD-C1) + motors board ( bobi board)
	public static final int SELECTED_DEVICE = SB_Q410_BOARD;        // android (qualcomm Q410) + motors board ( Andrew Kohlsmith board) 

	private int destID = -1;

	@SuppressWarnings("unused")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Log.d(TAG, "onCreate ...");
		context = this;
		switch (SELECTED_DEVICE) {
		default:
		case SB_VIRTUAL_DEVICE:
			// -------------- virtual device initialisation ------------------
			Button btnSpeak = (Button) findViewById(R.id.btnStart);
			mSwitchBotMcu = new SBVirtualDevice(btnSpeak);
			// ---------------------------------------------------------------
			break;
		case SB_REAL_DEVICE_MINI:
			// --------------static SwitchBot initialisation ------------------
			if (!SBRealDeviceMini.isConnected(this)) {
				Log.d(TAG, "switchbot mcu not connected, exiting program ...");
				this.finish();
				return;
			}
			mSwitchBotMcu = SBRealDeviceMini.getInstance(this);
			// ---------------------------------------------------------------
			break;
		case SB_REAL_DEVICE:
		case SB_Q410_BOARD:
			// -------------- complete version of SwitchBot initialisation ----
			if (SELECTED_DEVICE == SB_REAL_DEVICE) {
				SBRealDeviceFactory.setSelectedDevice(SB_REAL_DEVICE, context);
				if (!SBRealDevice.isConnected(this)) {
					Log.d(TAG,
							"switchbot mcu not connected, exiting program ...");
					this.finish();
					return;
				}
			} else {

				SBRealDeviceFactory.setSelectedDevice(SB_Q410_BOARD, context);
			}
			mSwitchBotMcu = SBRealDeviceFactory.getInstance();
			mLsClient = new PushServer();
			mLsClient.addLSClientListener(new PushServerListener(this,
					mLsClient));
			mCommHandler = new SBCommHandler(mLsClient, context);
			mCommHandler.setRobotListener();
			mCommServer = new CommServer(mCommHandler);
			mSwitchBotMcu.setCommHandler(mCommHandler);

			// --------------------------
			ArrayList<DPNode> nodes = mSwitchBotMcu.getBeaconsLabels();
			Log.d(TAG, " beacons " + nodes);
			break;

		}
		
	//	we wait for 10 seconds to make sure that we're connected to the internet after the system boot
		new Thread(new Runnable() {

			byte data[] = { SBProtocol.RGB_CTRL_COMMAND, 0x01, 0x02 };

			@Override
			public void run() {

                while(true){	
				mSwitchBotMcu.write(data);
				try {
					Thread.sleep(300);
					Log.d(TAG, " test uart "); 
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
                }
			}
		}).start();

		
	
		
		
		sheneticsInit();
		if (mAI == null) {
			Log.d(TAG, "SHE cannot create assistant, exiting program ...");
			this.finish();
			return;
		}

		mAI.login("sbot2@she.ai", "GoDogLabs2");
		setDisconnectListener();

		mSwitchBotMcu.addRobotListener(new SBUsbListener());
	}

	private void sheneticsInit() {
		mAI = new SHE(this) {
			@Override
			public void loginAuth(boolean isValid) {
				if (isValid) {
					ArrayList<DPNode> beacons = mSwitchBotMcu
							.getBeaconsLabels();
					try {
						JSONArray locs = new JSONArray();
						for (int i = 0; i < beacons.size(); ++i) {
							JSONObject loc = new JSONObject();
							loc.put("label", beacons.get(i).label);
							loc.put("id", beacons.get(i).id);

							locs.put(loc);
						}
						setLocations(locs);
						setLED(SBProtocol.RGB_SOLID_BLUE);
					} catch (Exception e) {
					}
				}
			}

			@Override
			public void listeningStart() {
				setLED(SBProtocol.RGB_SOLID_AMBER);
				super.listeningStart();
			}

			@Override
			public void listeningStop() {
				resetLED();
				super.listeningStop();
			}

			@Override
			public void thinkingStart() {
				Log.d(TAG, "thinkingStart ...");
				setLED(SBProtocol.RGB_FLASH_GREEN_SLOW);
				super.thinkingStart();
			}

			@Override
			public void thinkingStop() {
				Log.d(TAG, "thinkingStop ...");
				resetLED();
				super.thinkingStop();
			}

			@Override
			public void speakingStart() {
				Log.d(TAG, "speakinStart ...");
				setLED(SBProtocol.RGB_FLASH_GREEN_QUICK);
				super.speakingStart();
			}

			@Override
			public void speakingStop() {
				Log.d(TAG, "speakinStop ...");
				resetLED();
				super.speakingStop();
			}

			@Override
			public void SHEneticsListener(JSONObject data) {
				byte[] usbData = new byte[2];
				Log.d("SHE", "data" + data.toString());
				try {
					if (data.getString("type") == "location") {
						// Bedroom, Den, Dining Room, Entryway, Kitchen, Front
						// Door, Back Door, Side Door, Patio, Family Room,
						// Hallway, Living Room, Master Bedroom, Kids Bedroom,
						// Guest Room, Office, Upstairs, Downstairs, Basement
						destID = data.getInt("id");
						new Thread(new Runnable() {
							@Override
							public void run() {
								mSwitchBotMcu.driveTo(destID);
							}
						}).start();
					} else if (data.getString("type") == "control") {
						if (data.getString("mode").contentEquals(
								"MOTION_TURN_LEFT")) {
							usbData[0] = SBProtocol.TURN_LEFT;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("left");
							return;
						}

						if (data.getString("mode").contentEquals(
								"MOTION_TURN_RIGHT")) {
							usbData[0] = SBProtocol.TURN_RIGHT;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("right");
							return;
						}

						if (data.getString("mode").contentEquals(
								"MOTION_MOVE_FORWARD")) {
							usbData[0] = SBProtocol.MOVE_FORWARD;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("forward");
							return;
						}

						if (data.getString("mode").contentEquals(
								"MOTION_MOVE_BACKWARD")) {
							usbData[0] = SBProtocol.MOVE_BACKWARD;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("backward");
							return;
						}

						if (data.getString("mode").contentEquals(
								"MOTION_LEAN_FORWARD")) {
							usbData[0] = SBProtocol.LEAN_FORWARD;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("leaning foward");
							return;
						}

						if (data.getString("mode").contentEquals(
								"MOTION_LEAN_BACKWARD")) {
							usbData[0] = SBProtocol.LEAN_BACKWARD;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("leaning backward");
							return;
						}

						if (data.getString("mode").contentEquals("MODE_STAND")) {
							usbData[0] = SBProtocol.STAND_UP;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("standing up");
							return;
						}

						if (data.getString("mode").contentEquals("MODE_DANCE")) {
							usbData[0] = SBProtocol.DANCE;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("dancing");
							return;
						}

						if (data.getString("mode").contentEquals("MODE_KNEEL")) {
							usbData[0] = SBProtocol.KNEEL;
							usbData[1] = 2;
							mSwitchBotMcu.write(usbData);
							speak("kneeling");
							return;
						}
					}
				} catch (Exception e) {
				}
			}
		};
	}

	private void setDisconnectListener() {
		BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG, "onReceive ...");
				checkDevice();
			}

		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);
	}

	@Override
	protected void onStop() {
		Log.d(TAG, "onStop ...");
		super.onStop();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onPause ...");
		super.onStop();
		super.onPause();
	}

	protected void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy ...");
		if (mAI != null) {
			mAI.release();
		}
		 
		
	}

	@Override
	protected void onResume() {
		super.onResume();

		Log.d(TAG, "onResume ...");
		if (mLsClient != null) {
			mLsClient.onResume();
		}

	}

	private void setLED(int color) {
		if (mSwitchBotMcu == null)
			return;

		byte[] usbData = new byte[3];
		usbData[0] = SBProtocol.RGB_CTRL_COMMAND;
		usbData[1] = (byte) color;
		usbData[2] = 0;
		mSwitchBotMcu.write(usbData);
	}

	private void resetLED() {
		setLED(SBProtocol.RGB_SOLID_BLUE);
	}

	private class SBUsbListener implements RobotListener {

		@Override
		public void onNotify(RobotEvent e) {

			// Log.d(TAG, " " + Utils.bytesToHex2(e.getData()));
			byte cmd = e.getData()[0];

			Log.d(TAG, "-- input: " + Utils.bytesToHex2(e.getData()));
			switch (cmd) {
			case SBProtocol.NOTF_VOICE_RECORD: // intended to SHE

				Log.d(TAG, "btnStart ... ");

				MainActivity.this.runOnUiThread(new Runnable() {

					@Override
					public void run() {

						// ------------------ start of voice recording
						// ---------------------------
						
						
						if (mAI != null) {
							resetLED();
							mAI.startAsr();
						}
						
						 
					}
				});

				break;

			case SBProtocol.NOTF_DP_TARGET_REACHED: // intended to SHE
				if (mAI != null) {
					ArrayList<DPNode> beacons = mSwitchBotMcu
							.getBeaconsLabels();
					for (int i = 0; i < beacons.size(); ++i) {
						if (beacons.get(i).id == destID) {
							mAI.speak("I've reached the "
									+ beacons.get(i).label);
						}
					}
				}
 
				destID = -1;
				break;

			case SBProtocol.NOTF_MCU_UP: // intended to SHE
				resetLED();
				break;

			// ------------------------------------------------------------------

			case SBProtocol.NOTF_GET_STATUS:
				Log.d(TAG, "Notif get status ");
				handleGetStatus();
				break;

			case SBProtocol.NOTF_ACTIVATE_ADB:
				Log.d(TAG, "Notif activate ADB through wifi  ");
				try {
					Log.d(TAG, "activating adb through wifi, have root: ");
					AdbUtils.set(5555);
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}

				break;

			default:
				break;
			}
		}

	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Log.d(TAG, " onNewIntent ...");
		switch (SELECTED_DEVICE) {
		case SB_REAL_DEVICE:
			mSwitchBotMcu = SBRealDevice.getInstance(this);
			break;
		case SB_REAL_DEVICE_MINI:
			mSwitchBotMcu = SBRealDeviceMini.getInstance(this);
			break;
		default:
			break;
		}
		checkDevice();
		mSwitchBotMcu.removeListeners();
		mSwitchBotMcu.addRobotListener(new SBUsbListener());
		if (mCommHandler != null) {
			mCommHandler.setRobotListener();
		}

	}

	private void checkDevice() {

		switch (SELECTED_DEVICE) {
		case SB_REAL_DEVICE:
			if (SBRealDevice.isConnected(context)) {
				Log.d(TAG, "MCU connected ...");

			} else {
				mSwitchBotMcu.disconnect(DeviceType.NUTINY);
				Log.d(TAG, "MCU disconnected ...");
			}
			break;
		case SB_REAL_DEVICE_MINI:
			if (SBRealDeviceMini.isConnected(context)) {
				Log.d(TAG, "MCU connected ...");

			} else {
				mSwitchBotMcu.disconnect(DeviceType.NUTINY);
				Log.d(TAG, "MCU disconnected ...");
			}
			break;
		default:
			break;
		}
	}

	private void handleGetStatus() {
		if (!SBRealDevice.isConnected(this))
			return;
		byte status = 0x01; // usb connected
		if (Utils.isWifiConnected())
			status = (byte) (status | 0x02); // wifi connected
		if (mLsClient.isConnected())
			status = (byte) (status | 0x04); // telepresence connected

		// send status notification to mcu
		mSwitchBotMcu.writeRaw(new byte[] { SBProtocol.NOTF_SET_STATUS, status,
				0x00 });

	}

}


