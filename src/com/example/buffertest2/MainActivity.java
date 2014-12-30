package com.example.buffertest2;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnBufferingUpdateListener {

	StreamingMediaPlayer sPlayer;
	StreamProxy sProxy;
	MediaPlayer mPlayer;
	Button mPlayBtn;
	Button vimeoBtn;
	// Button singleBtn;
	Button wifiBtn;
	Button youtubeBtn;
	
	Button videoBtn1;
	Button videoBtn2;
	Button videoBtn3;
	Button videoBtn4;

    Button wifiBtn1;
    Button wifiBtn2;
    Button wifiBtn3;
    Button wifiBtn4;
    
    Button churnBtn1;
    Button churnBtn2;
    Button churnBtn3;
    Button churnBtn4;
	
	TextView tView;
	ProgressBar progress;
	SurfaceView sView;
	SurfaceHolder sHolder;

	/*
	 * Log & packet capture process
	 */
	// Process log_proc;
	Process packet_proc = null;
	File log_file = null;
	Time today = new Time(Time.getCurrentTimezone());
	String file_name = "";
	BufferedWriter buf_writter;
	PrintWriter file_log_out;
	int pid;

	ConnectivityManager connectivityManager;
	NetworkInfo mWifi;

	/*
	 * WIFI timer
	 */
	private Runnable wifiTurnOnOffTask;

	/*
	 * Odd : WiFi AP contact length Even : WiFi inter-AP contact length
	 * 
	 * First 0 means, there was no WiFi connection.
	 */

	int[] wifiArray = { 0, 31, 60, 240, 60, 60, 60, 120, 60, 120, 120, 120, 60,
			180, 60, 60, 60, 60, 240 };
	
	int currentWifiArrayIndex = 0;

	Handler[] wifiTurnOnOffHandlerArray = new Handler[wifiArray.length];

	static boolean wifiStatus = false;

	static SharedPreferences sharedSetting;

	private final static int INTERVAL = 100; // 0.1 seconds

	/*
	 * mHandler : to save wifi change status and batery level
	 */

	Handler mHandler = new Handler();
	Runnable loggingHandlerTask;

	long start_time;
	
	Intent batteryStatus;

	long initial_start_time = -1;
	String previousBattery = "100";

	boolean isFirst = true;
	boolean isConnected;

	void startRepeatingTask() {
		loggingHandlerTask.run();
	}

	void stopRepeatingTask() {
		mHandler.removeCallbacks(loggingHandlerTask);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			packet_proc = Runtime.getRuntime().exec("su");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		Window win = getWindow();
		win.requestFeature(Window.FEATURE_NO_TITLE);
		win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_main);

		sView = (SurfaceView) findViewById(R.id.surface);

		sHolder = sView.getHolder();

		mPlayBtn = (Button) findViewById(R.id.play);
		findViewById(R.id.play).setOnClickListener(mClickPlay);
		// findViewById(R.id.stop).setOnClickListener(mClickStop);

		wifiBtn = (Button) findViewById(R.id.wifi);
		//wifiBtn.setOnClickListener(wifiTurnOnOff);

		vimeoBtn = (Button) findViewById(R.id.vimeo);
		vimeoBtn.setOnClickListener(mVimeoClickPlay);

		youtubeBtn = (Button) findViewById(R.id.youtube);
		youtubeBtn.setOnClickListener(mYoutubeClickPlay);

		videoBtn1 = (Button) findViewById(R.id.video_button1);
		videoBtn1.setOnClickListener(mVideoButton1);
		videoBtn2 = (Button) findViewById(R.id.video_button2);
		videoBtn2.setOnClickListener(mVideoButton2);
		videoBtn3 = (Button) findViewById(R.id.video_button3);
		videoBtn3.setOnClickListener(mVideoButton3);

		wifiBtn1 = (Button) findViewById(R.id.wifi_button1);
        wifiBtn1.setOnClickListener(mwifiButton1);
        wifiBtn2 = (Button) findViewById(R.id.wifi_button2);
        wifiBtn2.setOnClickListener(mwifiButton2);
        wifiBtn3 = (Button) findViewById(R.id.wifi_button3);
        wifiBtn3.setOnClickListener(mwifiButton3);
        wifiBtn4 = (Button) findViewById(R.id.wifi_button4);
        wifiBtn4.setOnClickListener(mwifiButton4);

        churnBtn1 = (Button) findViewById(R.id.churn_button1);
        churnBtn1.setOnClickListener(mchurnButton1);
        churnBtn2 = (Button) findViewById(R.id.churn_button2);
        churnBtn2.setOnClickListener(mchurnButton2);
        churnBtn3 = (Button) findViewById(R.id.churn_button3);
        churnBtn3.setOnClickListener(mchurnButton3);
        churnBtn4 = (Button) findViewById(R.id.churn_button4);
        churnBtn4.setOnClickListener(mchurnButton4);
        
        
		tView = (TextView) findViewById(R.id.tView);
		progress = (ProgressBar) findViewById(R.id.progress);

		sProxy = new StreamProxy();
		sProxy.start();
		sPlayer = null;

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// server setting
		sharedSetting = PreferenceManager.getDefaultSharedPreferences(this);

		wifiTurnOnOffTask = new Runnable() {
			@Override
			public void run() {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// TODO Auto-generated method stub

						wifiStatus = !wifiStatus;

						WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
						wifiManager.setWifiEnabled(wifiStatus);

						if (wifiStatus) {
							wifiBtn.setText("On");
							//StreamingMediaPlayer.logWrite(true, 1);
							Log.i("carpedm20", "WIFI Turn On");
							Toast.makeText(getApplicationContext(), "WIFI Turn On", Toast.LENGTH_LONG).show();
						} else {
							wifiBtn.setText("Off");
							//StreamingMediaPlayer.logWrite(true, 0);
							Log.i("carpedm20", "WIFI Turn Off");
							Toast.makeText(getApplicationContext(), "WIFI Turn Off", Toast.LENGTH_LONG).show();
						}
					}
				});
			}
		};

		today.setToNow();
		file_name = "/sdcard/" + today.format("%Y%m%d_%H%M%S") + "_log.txt";

		connectivityManager = (ConnectivityManager) this
				.getApplicationContext().getSystemService(
						Context.CONNECTIVITY_SERVICE);
		mWifi = connectivityManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		isConnected = mWifi.isConnected();

		loggingHandlerTask = new Runnable() {
			@SuppressLint("DefaultLocale")
			@Override
			public void run() {
				if (isFirst) {
					String str = "0";
					start_time = System.currentTimeMillis();

					mWifi = connectivityManager
							.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

					boolean newIsConnected = mWifi.isConnected();

					if (newIsConnected)
						str += "\t1\t";
					else
						str += "\t0\t";
					
					String newBattery = String.format("%.2f", getBatteryLevel());
					str += newBattery;
					
					logWrite(str);
					
					previousBattery = newBattery;
					isConnected = newIsConnected;
					
					getPacketCapture(newIsConnected);
	
					Log.i("carpedm20", "[ log ] " + str);
					isFirst = false;
				} else {
					long timeDiff = System.currentTimeMillis() - start_time;
					String str = Long.toString(timeDiff)+"";
	
					mWifi = connectivityManager
							.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
	
					boolean newIsConnected = mWifi.isConnected();
	
					if (newIsConnected)
						str += "\t1\t";
					else
						str += "\t0\t";
					
					String newBattery = String.format("%.2f", getBatteryLevel());
					str += newBattery;
	
					if (!previousBattery.equals(newBattery)
							|| isConnected != newIsConnected) {
						previousBattery = newBattery;
						isConnected = newIsConnected;
	
						destroyPacketCaputre();
						getPacketCapture(newIsConnected);
	
						logWrite(str);
	
						Log.i("carpedm20", "[ log ] " + str);
					}
				}
				
				mHandler.postDelayed(loggingHandlerTask, INTERVAL);
			}
		};
	}
	
	public float getBatteryLevel() {
	    Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
	    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

	    // Error checking that probably isn't needed but I added just in case.
	    if(level == -1 || scale == -1) {
	        return 50.0f;
	    }

	    return ((float)level / (float)scale) * 100.0f; 
	}

	/*public double getBatteryLevel() {
		batteryStatus = getContext().registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		float batteryPct = level / (float) scale;
		Log.d("carpedm20", "[ log ] battery : " + (batteryPct * 100.0));
		return (batteryPct * 100.0);
	}*/
	
	public void logWrite(String str) {
		try {
			buf_writter = new BufferedWriter(new FileWriter(file_name, true));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		file_log_out = new PrintWriter(buf_writter);
		file_log_out.println(str);
		file_log_out.close();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		String killCommand = "kill "+pid;  
		Process process2;
		
		try {
			process2 = Runtime.getRuntime().exec("su");
			
			DataOutputStream os = new DataOutputStream(process2.getOutputStream());  
			os.writeBytes(killCommand);  
			
			os.flush();  
			os.writeBytes("exit\n");  
			os.flush();  
			os.close();  
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		destroyPacketCaputre();
		file_log_out.flush();
	}

	public Context getContext() {
		return this.getApplicationContext();
	}

	public void setText(String d1) {
		tView.setText(d1);
	}

	@SuppressWarnings("deprecation")
	public void getPacketCapture(boolean isWifi) {
		String cmd = "";
		
		try {
			packet_proc = Runtime.getRuntime().exec("su");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if (isWifi)
			cmd = "tcpdump -i wlan0 -s 0 > /sdcard/"
					+ today.format("%Y%m%d_%H%M%S") + "_wifi.pcap";
		else
			cmd = "tcpdump -i rmnet1 -s 0 > /sdcard/"
					+ today.format("%Y%m%d_%H%M%S") + "_lte.pcap";

		try {
			packet_proc = Runtime.getRuntime().exec(cmd);

			DataOutputStream os = new DataOutputStream(
					packet_proc.getOutputStream());
			os.writeBytes(cmd);
			os.flush();
			os.writeBytes("exit\n");
			os.flush();
			os.close();

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			/*
			 * get the pid of the process in which we exec tcpdump to do that we
			 * use the ps command, so we need to launch another process to
			 * achieve that
			 */
			
			Process process2 = Runtime.getRuntime().exec("ps tcpdump");
			
			// read the output of ps
			DataInputStream in = new DataInputStream(process2.getInputStream());
			@SuppressWarnings("deprecation")
			String temp = in.readLine();
			temp = in.readLine();
			
			// We apply a regexp to the second line of the ps output to get the
			// pid
			temp = temp.replaceAll("^root *([0-9]*).*", "$1");
			pid = Integer.parseInt(temp);

			// the ps process is no more needed
			process2.destroy();

			Log.d("carpedm20", "[ log ] Packet capture start : " + cmd);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.d("carpedm20", "[ log ] Packet capture start failed");
		}
	}

	public void destroyPacketCaputre() {
		if (packet_proc != null) {
			packet_proc.destroy();

			Log.d("carpedm20", "[ log ] Packet capture stop");
		} else {
			Log.d("carpedm20", "[ log ] Packet capture stop failed");
		}
	}

	public void start() {
		
		try {
			// sPlayer.startStreaming("http://10.20.16.52/video/sample1.mp4",
			// 26744, 289);
			// sPlayer.startStreaming("http://10.20.16.52/video/sample2.mp4",
			// 1161451, 3790);
			// sPlayer.startStreaming("http://moza.us.to/~carpedm20/video/star.mp4",
			// 7606323, 3546);
			// sPlayer.startStreaming("http://moza.us.to/~carpedm20/video/sample1.mp4",
			// 7606323, 3546);
			// sPlayer.startStreaming("http://moza.us.to/~carpedm20/video/flower.mp4",
			// 9310932, 3600+8*60+26);
			// sPlayer.startStreaming("http://10.20.13.249/~tunz/flower.mp4",
			// 2680155898L, 3600+8*60+26);
			// sPlayer.startStreaming("http://msn.unist.ac.kr/proxy/flower.mp4",
			// 2680155898L, 3600+8*60+26);
			// sPlayer.startStreaming("http://msn.unist.ac.kr/inception.mp4",
			// 89992675L, 2*60+21);
			// sPlayer.startStreaming("http://moza.us.to/~tunz/inception.mp4",
			// 108010885, 2*60+23);
			sPlayer.startStreaming();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/*
	 * Wifi Turn on off
	 */

	/*Button.OnClickListener wifiTurnOnOff = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			wifiBtn.setEnabled(false);

			WifiManager wifiManager = (WifiManager) getApplicationContext()
					.getSystemService(Context.WIFI_SERVICE);
			wifiManager.setWifiEnabled(false);

			wifiStatus = true;

			int cummulativeWaitTime = 0;

			for (int i = 0; i < wifiArray.length; i++) {
				cummulativeWaitTime += wifiArray[i] * 1000;

				wifiTurnOnOffHandlerArray[i] = new Handler();
				wifiTurnOnOffHandlerArray[i].postDelayed(wifiTurnOnOffTask,
						cummulativeWaitTime);
			}
		}
	};*/

	Button.OnClickListener mClickPlay = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.i("carpedm20", "START");
			sPlayer = new StreamingMediaPlayer(getContext(), tView, mPlayBtn,
					progress, sHolder);

			StreamingMediaPlayer.isPlaying = true;

			mPlayBtn.setText("Played");
			mPlayBtn.setEnabled(false);
			// singleBtn.setEnabled(false);
			youtubeBtn.setEnabled(false);
			vimeoBtn.setEnabled(false);

			sPlayer.MBCMode = true;

			loggingHandlerTask.run();

			start();
		}
	};

	Button.OnClickListener mVimeoClickPlay = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.i("carpedm20", "START");
			sPlayer = new StreamingMediaPlayer(getContext(), tView, mPlayBtn,
					progress, sHolder);
			StreamingMediaPlayer.isPlaying = true;

			mPlayBtn.setText("Played");
			mPlayBtn.setEnabled(false);
			// singleBtn.setEnabled(false);
			youtubeBtn.setEnabled(false);
			vimeoBtn.setEnabled(false);

			sPlayer.vimeoMode = true;

			start();
		}
	};

	Button.OnClickListener mYoutubeClickPlay = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.i("carpedm20", "START");
			sPlayer = new StreamingMediaPlayer(getContext(), tView, mPlayBtn,
					progress, sHolder);
			StreamingMediaPlayer.isPlaying = true;

			mPlayBtn.setText("Played");
			mPlayBtn.setEnabled(false);
			youtubeBtn.setEnabled(false);
			vimeoBtn.setEnabled(false);

			sPlayer.youtubeMode = true;

			start();
		}
	};

	/*Button.OnClickListener mClickStop = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (sPlayer != null) {
				StreamingMediaPlayer.isPlaying = false;
				sPlayer.getMediaPlayer().stop();
				sPlayer.mediaPlayer = null;
			}

			mPlayBtn.setEnabled(true);
			// singleBtn.setEnabled(true);
			youtubeBtn.setEnabled(true);
			vimeoBtn.setEnabled(true);

			sPlayer = null;

			File downloadingMediaFile = new File(getContext().getCacheDir(),
					"downloadingMedia.dat");
			if (downloadingMediaFile.exists()) {
				downloadingMediaFile.delete();
			}
		}
	};*/

	Button.OnClickListener mVideoButton1 = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			videoBtn1.setEnabled(false);
			videoBtn2.setEnabled(true);
			videoBtn3.setEnabled(true);
			StreamingMediaPlayer.base_bitrate = 4609;
			StreamingMediaPlayer.base_filesize = 345731583;
			StreamingMediaPlayer.base_time = 10 * 60;
			StreamingMediaPlayer.base_url = "http://msn.unist.ac.kr/videos/CBR_720p.mp4";
		}
	};
	Button.OnClickListener mVideoButton2 = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			videoBtn1.setEnabled(true);
			videoBtn2.setEnabled(false);
			videoBtn3.setEnabled(true);
			StreamingMediaPlayer.base_bitrate = 2007;
			StreamingMediaPlayer.base_filesize = 160256490;
			StreamingMediaPlayer.base_time = 10 * 60;
			StreamingMediaPlayer.base_url = "http://msn.unist.ac.kr/videos/CBR_480p_1.mp4";
		}
	};
	Button.OnClickListener mVideoButton3 = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			videoBtn1.setEnabled(true);
			videoBtn2.setEnabled(true);
			videoBtn3.setEnabled(true);
			
			StreamingMediaPlayer.base_bitrate = 1023;
			StreamingMediaPlayer.base_filesize = 86476937;
			StreamingMediaPlayer.base_time = 10 * 60;
			StreamingMediaPlayer.base_url = "http://msn.unist.ac.kr/videos/CBR_360p.mp4";
		}
	};

	Button.OnClickListener mwifiButton1 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            wifiBtn1.setEnabled(false);
            wifiBtn2.setEnabled(true);
            wifiBtn3.setEnabled(true);
            wifiBtn4.setEnabled(true);
			
            wifiArray = new int []{0,    31,    60,   240,    60,    60,    60,    89 };
        	
        	WifiManager wifiManager = (WifiManager) getApplicationContext()
					.getSystemService(Context.WIFI_SERVICE);
			wifiManager.setWifiEnabled(false);

			wifiStatus = true;

			int cummulativeWaitTime = 0;

			for (int i = 0; i < wifiArray.length; i++) {
				cummulativeWaitTime += wifiArray[i] * 1000;

				wifiTurnOnOffHandlerArray[i] = new Handler();
				wifiTurnOnOffHandlerArray[i].postDelayed(wifiTurnOnOffTask,
						cummulativeWaitTime);
			}
        }
    };
    
    Button.OnClickListener mwifiButton2 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            wifiBtn1.setEnabled(true);
            wifiBtn2.setEnabled(false);
            wifiBtn3.setEnabled(true);
            wifiBtn4.setEnabled(true);
			
            wifiArray = new int []{0,    31,    60,   120,   120,   120,    60,    89 };
        	
        	WifiManager wifiManager = (WifiManager) getApplicationContext()
					.getSystemService(Context.WIFI_SERVICE);
			wifiManager.setWifiEnabled(false);

			wifiStatus = true;

			int cummulativeWaitTime = 0;

			for (int i = 0; i < wifiArray.length; i++) {
				cummulativeWaitTime += wifiArray[i] * 1000;

				wifiTurnOnOffHandlerArray[i] = new Handler();
				wifiTurnOnOffHandlerArray[i].postDelayed(wifiTurnOnOffTask,
						cummulativeWaitTime);
			}
        }
    };
    
    Button.OnClickListener mwifiButton3 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            wifiBtn1.setEnabled(true);
            wifiBtn2.setEnabled(true);
            wifiBtn3.setEnabled(false);
            wifiBtn4.setEnabled(true);
			
            wifiArray = new int []{0,    91,    60,    60,    60,    60,   240,    29 };
        	
        	WifiManager wifiManager = (WifiManager) getApplicationContext()
					.getSystemService(Context.WIFI_SERVICE);
			wifiManager.setWifiEnabled(false);

			wifiStatus = true;

			int cummulativeWaitTime = 0;

			for (int i = 0; i < wifiArray.length; i++) {
				cummulativeWaitTime += wifiArray[i] * 1000;

				wifiTurnOnOffHandlerArray[i] = new Handler();
				wifiTurnOnOffHandlerArray[i].postDelayed(wifiTurnOnOffTask,
						cummulativeWaitTime);
			}
        }
    };
    
    Button.OnClickListener mwifiButton4 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            wifiBtn1.setEnabled(true);
            wifiBtn2.setEnabled(true);
            wifiBtn3.setEnabled(true);
            wifiBtn4.setEnabled(false);
			
        	wifiArray = new int []{ 0, 31, 60, 240, 60, 60, 60, 120, 60, 120, 120, 120, 60, 180, 60, 60, 60, 60, 240 };
        	
        	WifiManager wifiManager = (WifiManager) getApplicationContext()
					.getSystemService(Context.WIFI_SERVICE);
			wifiManager.setWifiEnabled(false);

			wifiStatus = true;

			int cummulativeWaitTime = 0;

			for (int i = 0; i < wifiArray.length; i++) {
				cummulativeWaitTime += wifiArray[i] * 1000;

				wifiTurnOnOffHandlerArray[i] = new Handler();
				wifiTurnOnOffHandlerArray[i].postDelayed(wifiTurnOnOffTask,
						cummulativeWaitTime);
			}
        }
    };


    
    Button.OnClickListener mchurnButton1 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            churnBtn1.setEnabled(false);
            churnBtn2.setEnabled(true);
            churnBtn3.setEnabled(true);
            churnBtn4.setEnabled(true);
			
            MaximumBufferControl.churn_time_array = new double [] { 0.01, 0.02, 0.04, 0.045, 0.05,
            	    0.08, 0.09, 0.12, 0.2, 0.22,
            	    0.23, 0.28, 0.74, 0.76, 0.82,
            	    0.98, 0.985, 0.99, 0.991, 0.992,
            	    0.99, 0.994, 0.995, 0.996, 0.997,
            	    0.99, 0.999, 1, 1, 1,
            	    1, 1, 1, 1, 1,
            	    1, 1, 1, 1, 1,
            	    1, 1, 1, 1, 1,
            	    1, 1, 1, 1, 1};
        }
    };
	
    
    Button.OnClickListener mchurnButton2 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            churnBtn1.setEnabled(true);
            churnBtn2.setEnabled(false);
            churnBtn3.setEnabled(true);
            churnBtn4.setEnabled(true);
			
            MaximumBufferControl.churn_time_array = new double [] { 0.6472, 0.6544, 0.6616, 0.6688, 0.6760,
							    0.6832, 0.6904, 0.6976, 0.7048, 0.712,
							    0.7192, 0.7264, 0.7336, 0.7408, 0.748,
							    0.7552, 0.7624, 0.7696, 0.7768, 0.784,
							    0.7912, 0.7984, 0.8056, 0.8128, 0.82,
							    0.8272, 0.8344, 0.8416, 0.8488, 0.856,
							    0.8632, 0.8704, 0.8776, 0.8848, 0.892,
							    0.8992, 0.9064, 0.9136, 0.9208, 0.928,
							    0.9352, 0.9424, 0.9496, 0.9568, 0.964,
							    0.9712, 0.9784, 0.9856, 0.9928, 1};
        }
    };
	
    Button.OnClickListener mchurnButton3 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            churnBtn1.setEnabled(true);
            churnBtn2.setEnabled(true);
            churnBtn3.setEnabled(false);
            churnBtn4.setEnabled(true);
			
            MaximumBufferControl.churn_time_array = new double [] { 0.01, 0.0133, 0.0167, 0.02, 0.0267,
            	    0.0333, 0.04, 0.0467, 0.0533, 0.06,
            	    0.07, 0.08, 0.092, 0.105, 0.118,
            	    0.13, 0.15, 0.165, 0.18, 0.21,
            	    0.23, 0.252, 0.27, 0.29, 0.31,
            	    0.34, 0.37, 0.4, 0.44, 0.48,
            	    0.52, 0.56, 0.62, 0.68, 0.72,
            	    0.77, 0.82, 0.9, 0.98, 0.995,
            	    1, 1, 1, 1, 1,
            	    1, 1, 1, 1, 1};
        }
    };
	
    Button.OnClickListener mchurnButton4 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            churnBtn1.setEnabled(true);
            churnBtn2.setEnabled(true);
            churnBtn3.setEnabled(true);
            churnBtn4.setEnabled(false);
			
            MaximumBufferControl.churn_time_array = new double [] { 0.01, 0.0125, 0.015, 0.0175, 0.02,
                    0.025, 0.03, 0.04, 0.045, 0.05,
                    0.06, 0.0625, 0.0650, 0.0675, 0.07,
                    0.0733, 0.0767, 0.08, 0.095, 0.0975,
                    0.1, 0.11, 0.12, 0.125, 0.13,
                    0.14, 0.16, 0.175, 0.18, 0.19,
                    0.2, 0.25, 0.26, 0.28, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1};
        }
    };
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// switch (item.getItemId()) {

		// case R.menu.main:
		Intent i = new Intent(this, SettingActivity.class);
		startActivityForResult(i, 1);

		return true;
	}

	@Override
	public void onBufferingUpdate(MediaPlayer arg0, int arg1) {
		// TODO Auto-generated method stub
		Log.i("tunz", "Buffer: " + String.valueOf(arg1));

	}

}
