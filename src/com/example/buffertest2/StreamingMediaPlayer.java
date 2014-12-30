package com.example.buffertest2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * MediaPlayer does not yet support streaming from external URLs so this class provides a pseudo-streaming function
 * by downloading the content incrementally & playing as soon as we get enough audio in our temporary storage.
 */
public class StreamingMediaPlayer implements OnPreparedListener {
	
	static long player_start_time = 0;
	
	// setting
	//static int base_bitrate = 556; //543k; // including file header: 694kbps
	//static long base_filesize = 38119410 ;
	//static int base_time = 9*60+5;
	//static String base_url = "http://msn.unist.ac.kr/proxy/inception2.mp4";
	
	
	//static int base_bitrate = 4481; // kbps
	static int base_bitrate = 3450; // kbps
	static long base_filesize = 258750000;
	//static int base_bitrate = 6900; // kbps
	//static long base_filesize = 517500000;
	static int base_time = 600;
	static String base_url = "http://msn.unist.ac.kr/videos/CBR_720p.mp4";
	public static long headerSize = 0;
	
	
	/*
	static int base_bitrate = 2007; // kbps
	static long base_filesize = 160256490;
	static int base_time = 10*60;
	static String base_url = "http://msn.unist.ac.kr/videos/CBR_480p_1.mp4";
	*/
	
	/*
	static int base_bitrate = 2407; // kbps
	static long base_filesize = 38053892;
	static int base_time = 2*60;
	static String base_url = "http://msn.unist.ac.kr/videos/CBR_480p_2.mp4";
	
	static int base_bitrate = 1023; // kbps
	static long base_filesize = 86476937;
	static int base_time = 10*60;
	static String base_url = "http://msn.unist.ac.kr/videos/CBR_360p.mp4";
	*/
	
	
	// chrun ratio
	// x^(-(base_time)*0.7)=0.8
	// x^(-(2*60+23)*0.7)=0.8
	static final double base_x = 1.00695;
	static double temp_churn_prob = -1;
	
    private static final int INITIAL_KB_BUFFER =  2000;

	private TextView textStreamed;
	
	private Button playButton;
	
	private ProgressBar	progressBar;
	
	private SurfaceHolder holder;
	
	//  Track for display by progressBar
	private long mediaLengthInKb, mediaLengthInSeconds, mediaLengthInByte;
	
	// Create Handler to call View updates on the main UI thread.
	private final Handler handler = new Handler();

	
	private File downloadingMediaFile; 
	
	private boolean isInterrupted;
	
	private Context context;
	
	public static boolean isPlaying = false;
	public long totalKbRead = 0;
	public static int totalBytesRead = 0;
	
	public long maxBufferSize = 0;
	public long expectedBufferTime = 0;
	public long currentPosition = 0;
	
	public MediaPlayer mediaPlayer;
	
	private int duration = 0;
	public boolean getDuration = false;
	
	private TimerTask mTask;
	private Timer mTimer;
	public boolean timerStarted = false;
	
	int sleep_loop_count = 5;
	
	public boolean vimeoMode = false;
	public boolean youtubeMode = false;
	public boolean singleMode = false;
	public boolean MBCMode = false;
	
	boolean climb = true;

    long loop_count = 0;
	int loop_index = 0;
	boolean MBC_SKIP = false;
	
	boolean downloadFinished = false;
	
	int batteryLevel = -1;

	/* maximum buffer control 2014.03.23 */
	List<Integer> MBC_decision = new ArrayList<Integer>();
	int list_index = 0;

	boolean is_MBC_first = true;
	/* maximum buffer control 2014.03.23 */

	// for debug
	boolean DOWNLOAD_CHECK_MODE = false;
	
	int previous_MBC_decision = -1; 
	int current_MBC_decision = -1;

	public static Time today = new Time(Time.getCurrentTimezone());
	public static String file_name = "";
	
	static Runnable loggingHandlerTask = null;
	Handler mHandler = new Handler();
	private final static int INTERVAL = 1000; // 1 seconds
	
	public static BufferedWriter buf_writter;
	public static PrintWriter file_log_out;
	State previous_wifi;
	
 	public StreamingMediaPlayer(Context  context,TextView textStreamed, Button	playButton,ProgressBar progressBar, SurfaceHolder holder) 
 	{
		
 		this.context = context;
		this.textStreamed = textStreamed;
		this.playButton = playButton;
		this.progressBar = progressBar;
		this.holder = holder;
		
		if (loggingHandlerTask == null) {
			today.setToNow();

			if (file_name.equals("") == true) {
				file_name = "/sdcard/download_" + today.format("%Y%m%d_%H%M%S") + "_log.txt";
			}
			
			loggingHandlerTask = new Runnable() {
				@SuppressLint("DefaultLocale")
				@Override
				public void run() {
					logWrite(false, -1);
					
					mHandler.postDelayed(loggingHandlerTask, INTERVAL);
				}
			};
			loggingHandlerTask.run();
		}
		
    	ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    	previous_wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		logWrite(true, 0);
	}
	
	public static void logWrite(boolean wifi, int mode) {
		try {
			if (file_name.equals("") == true) {
				today.setToNow();
				file_name = "/sdcard/download_" + today.format("%Y%m%d_%H%M%S") + "_log.txt";
			}
			buf_writter = new BufferedWriter(new FileWriter(file_name, true));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Long tsLong = System.currentTimeMillis()/1000;
		String ts = tsLong.toString();
		file_log_out = new PrintWriter(buf_writter);
		
		String str = ts+"\t" +((totalBytesRead - headerSize)/1024);
		if (wifi) {
			if (mode == 0) {
				str += "\tWifiOff";
			} else {
				str += "\tWifiOn";
			}
		}
		file_log_out.println(str);
		file_log_out.close();
	}

	
    /**  
     * Progressivly download the media to a temporary location and update the MediaPlayer as new content becomes available.
     * @throws InterruptedException 
     */  
    //public void startStreaming(final String mediaUrl, long	mediaLengthInByte, long	mediaLengthInSeconds) throws IOException {
 	public void startStreaming() throws IOException {
    	
 		final String mediaUrl = MainActivity.sharedSetting.getString("video_url", StreamingMediaPlayer.base_url);
 		this.mediaLengthInKb = MainActivity.sharedSetting.getLong("video_size", StreamingMediaPlayer.base_filesize)/1024;
 		this.mediaLengthInByte = MainActivity.sharedSetting.getLong("video_size", StreamingMediaPlayer.base_filesize);
 		this.mediaLengthInSeconds = MainActivity.sharedSetting.getLong("video_length", StreamingMediaPlayer.base_time);

    	Log.i("carpedm20", "FILE init");
		downloadingMediaFile = new File(context.getCacheDir(),"downloadingMedia.dat");
		
        if (downloadingMediaFile.exists()) {
			downloadingMediaFile.delete();
			Log.i("carpedm20", "FILE DELETED");
		}
        
        final FileOutputStream out = new FileOutputStream(downloadingMediaFile,true);
        
		Runnable r = new Runnable() {
	        public void run() {
	        	while (true) {
		            try {
		        		downloadAudioIncrement(mediaUrl, out);
		            } catch (IOException e) {
		            	if (downloadFinished)
			            	break;
		            	Log.e(getClass().getName(), "Unable to initialize the MediaPlayer for fileUrl=" + mediaUrl, e);
		            }
		            
		            if (downloadFinished)
		            	break;
	        	}
	        }   
	    };
	    
	    Thread t = new Thread(r);
	    t.start();
    }
    
    /**  
     * Download the url stream to a temporary location and then call the setDataSource  
     * for that local file
     */  
    @SuppressLint("NewApi")
	public void downloadAudioIncrement(String mediaUrl, FileOutputStream out) throws IOException {
    	//long bitrate = 5730; // kbps
    	long bitrate = MainActivity.sharedSetting.getLong("video_bitrate", StreamingMediaPlayer.base_bitrate);
    	//headerSize = (long)2*1024*1024;
    	//headerSize = (long)0x995D0;
    	
    	ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		    	
    	long start_time = System.currentTimeMillis();
    	Calendar c = Calendar.getInstance();
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
    	String strDate = sdf.format(c.getTime());
    	File logFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/log_"+strDate+".txt");
    	if (logFile.exists()) {
			logFile.delete();
			logFile.createNewFile();
		}
    	FileOutputStream logOut = new FileOutputStream(logFile);
    	
        int current_offset = 0;
        
        // Setting chunk size and initial burst size
        long INITIAL_BURST = 1024*512;
	    long DOWNLOAD_SIZE = 0;
	    if (youtubeMode)
	    {
	    	INITIAL_BURST = 5000*1024; // 5MB
	    	DOWNLOAD_SIZE = 500*1024; // 50KB
	    }
	    else if(MBCMode)
	    {
	    	INITIAL_BURST = 5000*1024; // 5MB
	    	DOWNLOAD_SIZE = 5000*1024; // 500KB
	    }
	    else if (vimeoMode)
	    {
	    	//DOWNLOAD_SIZE = 22*1024*1024; // 22MB
	    	DOWNLOAD_SIZE = 22*1024*1024; // 50MB
	    }
	    
	    int TMP_DOWNLOAD_SIZE = (int)DOWNLOAD_SIZE;
	    
	    long last_txt_update = 0;
	    
	    // Setting loop count
	    long loop_count = 0;
	    loop_count = (long) ((mediaLengthInByte - INITIAL_BURST)/DOWNLOAD_SIZE + 1);
	    
	    if (youtubeMode || MBCMode)
	     	loop_count = (long) ((mediaLengthInByte - INITIAL_BURST)/DOWNLOAD_SIZE + 1);
	    else if (vimeoMode)
	    {
	    	loop_count = (long) mediaLengthInByte/DOWNLOAD_SIZE;
	    	if (mediaLengthInByte % DOWNLOAD_SIZE != 0)
	    		loop_count += 1;
	    }
	    
	    //Log.i("tunz",String.valueOf(loop_count));
	    
	    for (; loop_index<loop_count || totalBytesRead < mediaLengthInByte; loop_index++) {
			if (loop_index==0 && (youtubeMode || MBCMode))
				DOWNLOAD_SIZE = INITIAL_BURST;
			
			if (!isPlaying) {
				break;
			}
			
			//Log.i("tunz",String.valueOf(i)+", "+String.valueOf(DOWNLOAD_SIZE));
			
			// after player starts, determine whether keep downloading or stop
			if (mediaPlayer != null) {
				if (!isPlaying) {
					Log.i("playing", "not playing");
					interrupt();
		        	totalKbRead = 0;
		        	totalBytesRead = 0;
		        	mediaPlayer = null;
		        	//playButton.setEnabled(true);
					break;
				}
				Log.i("playing", "playing");
				
				//currentPosition = mediaPlayer.getCurrentPosition();
				currentPosition = ( System.currentTimeMillis() - player_start_time  );
				//expectedBufferTime = (long)(mediaPlayer.getDuration()*(totalBytesRead - headerSize)/(mediaLengthInByte - headerSize));
				expectedBufferTime = (long)(( mediaLengthInSeconds*1000 )*(totalBytesRead - headerSize)/(mediaLengthInByte - headerSize));

				//Log.i("tunz",String.valueOf(i)+", "+String.valueOf(currentPosition)+", "+String.valueOf(expectedBufferTime));
				
				if (youtubeMode)
				{
					if (totalBytesRead > INITIAL_BURST) 
					{
						// after initial burst
						// download as playbackrate * 1.25 bitrate
						maxBufferSize = (long) (INITIAL_BURST +((currentPosition/1000)*1.25*bitrate/8)*1024);
						
						if ((totalBytesRead - headerSize) > maxBufferSize)
						{
							try {
								Thread.sleep(500);
							} catch(InterruptedException e) {
								System.out.println(e.getMessage());
							}
							loop_index -= 1;
							continue;
						}
					}
				}
				else if (vimeoMode)
				{
					maxBufferSize = (long) ((loop_index+1)*DOWNLOAD_SIZE - headerSize);
					
					if (loop_index > 0)
					{
						if (expectedBufferTime - currentPosition > 3000 )
						{
							try {
								Thread.sleep(500);
							} catch(InterruptedException e) {
								System.out.println(e.getMessage());
							}
							loop_index -= 1;
							continue;
						}
					}
					
					//maxBufferSize = (long) ((loop_index+1)*DOWNLOAD_SIZE - headerSize);
				}
				else if (MBCMode)
				{
					maxBufferSize = -1;
					/*ConnectivityManager connectivityManager 
				              = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
					connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			        NetworkInfo mWifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);*/
					   
					State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
					
					if (is_MBC_first) {
				        //if(mWifi.isConnected()) {
						while (wifi == NetworkInfo.State.CONNECTING) {
							wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
						}
						if(wifi == NetworkInfo.State.CONNECTED) { // || wifi == NetworkInfo.State.CONNECTING) {
				        	if (expectedBufferTime - currentPosition < 3000) {
				        		//MBC_decision.add(1);
				        		current_MBC_decision = 1;
				        	} else {
				        		//MBC_decision.add(3);
				        		current_MBC_decision = 3;
				        	}
				        } else {
				        	//MBC_decision.add(1);
				        	current_MBC_decision = 1;
				        }

						is_MBC_first = false;
					/* else if t > 1 && t <= churn_time */
					} else {
						/* if received_size(t-1) < churn_time * playback_rate
						   if received_size(t-1) < video_size */
						previous_MBC_decision = current_MBC_decision;
						
						//Log.i("carpedm20", " [0] Previous MBC decision : " + previous_MBC_decision);
						
						//double playback_received_size = currentPosition * base_bitrate / 8.0;
						long received_size = currentPosition * base_bitrate * 1000 / 8;
						
						//if (mWifi.isConnected()) {

						while (wifi == NetworkInfo.State.CONNECTING) {
							wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
						}
						if(wifi == NetworkInfo.State.CONNECTED) { // || wifi == NetworkInfo.State.CONNECTING) {
							//Log.i("carpedm20", " [1] Wifi is connected!!!!!!!!!!!!!!!!!!");
							
							if (previous_MBC_decision == 3 || previous_MBC_decision == 0) {
								
								//Log.i("carpedm20", " [2] totalBytesRead + 1024*1024 - playback_received_size: " + (totalBytesRead + 1024*1024 - received_size));
								
								//if (totalBytesRead + 1024*1024 < playback_received_size) {
									//MBC_decision.add(1);
								//	current_MBC_decision = 1;
								//} else {
									//MBC_decision.add(3);
									current_MBC_decision = 3;
								//}
							// } else if (previous_MBC_decision == 1) {
							} else {
								/* if received_size(t-1) < playback_received_size(t) + min_buffer_threshold */

								//Log.i("carpedm20", " [2] totalBytesRead - (playback_received_size + DOWNLOAD_SIZE) : " + (totalBytesRead  + DOWNLOAD_SIZE - (received_size)));
								
								//if (totalBytesRead + DOWNLOAD_SIZE > received_size) {
								if (expectedBufferTime - currentPosition < 3000 ) {	
									//if (mWifi.isConnected()) {
									while (wifi == NetworkInfo.State.CONNECTING) {
										wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
									}
									if(wifi == NetworkInfo.State.CONNECTED) {// || wifi == NetworkInfo.State.CONNECTING) {
										//MBC_decision.add(1);
										current_MBC_decision = 3;
									} else {
										//MBC_decision.add(3);
										current_MBC_decision = 1;
									}
								} else {
									//MBC_decision.add(3);
									current_MBC_decision = 3;
								}
							}
						} else {
							//Log.i("carpedm20", " [1] Wifi is not connected");
							
							if (previous_MBC_decision == 3 || previous_MBC_decision == 0) {
								//Log.i("carpedm20", " [2] expectedBufferTime - currentPosition : " + (expectedBufferTime - currentPosition));
								
								//if (totalBytesRead + DOWNLOAD_SIZE > received_size) {
								if (expectedBufferTime - currentPosition < 3000 ) {
									MBC_decision.add(1);
									current_MBC_decision = 1;
								} else {
									current_MBC_decision = 0;
								}
							} else {
								boolean isBufferStop = MaximumBufferControl.calcMaximumBuffer_isStop( totalBytesRead, (int)(currentPosition/1000));
								
								//Log.i("carpedm20", " [2] calcMaximumBuffer_isStop : " + isBufferStop);
								
								if (isBufferStop) {
									//MBC_decision.add(1);
									//current_MBC_decision = 1;
									//MBC_decision.add(0);
									current_MBC_decision = 0;
									//MBC_decision.add(0);
									//current_MBC_decision = 0;
									//current_MBC_decision = 1; 
								}
							}
						}
					}

					if (current_MBC_decision == 0) {
						climb = false;
						try {
							Thread.sleep(500);
						} catch(InterruptedException e) {
							System.out.println(e.getMessage());
						}
						loop_index -= 1;
						Log.i("carpedm20", loop_index + " ========================SKIP 0========================");
						continue;
					}
					else if (current_MBC_decision == 1) {
						climb = true;
						
						Log.i("carpedm20", loop_index + " ======================1========================");
					}
					else if (current_MBC_decision == 2) {
						climb = true;
					
						Log.i("carpedm20", loop_index + " ========================2========================");
					}
					else if (current_MBC_decision == 3) {
						climb = true;
					
						Log.i("carpedm20", loop_index + " ========================3========================");
					}
				}
			}
			
			//if (loop_index == 0)
				//Log.i("carpedm20", "[" + loop_index + "] " + current_offset + "-" + (current_offset + DOWNLOAD_SIZE) + " SIZE : " + downloadingMediaFile.length());

			ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			
			if (MBCMode) {
				//if (MBC_decision.size() != 0) {
				if (current_MBC_decision != -1) {
					//int decision = MBC_decision.get(MBC_decision.size() - 1);
					int decision = previous_MBC_decision;
					
					if(decision == 1 || decision == 2) {
						
						boolean wifiEnabled = mWifi.isConnected();
						
						if (wifiEnabled) {
							WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
							wifiManager.setWifiEnabled(false);
							logWrite(true, 1);
							Log.i("carpedm20", "~~~~~~~~~~~~~~~~~~~WIFI OFF~~~~~~~~~~~~~~~~~~~");
						}
					} else if (decision == 3) {
						boolean wifiEnabled = mWifi.isConnected();
						
						if (!wifiEnabled) {
							WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
							wifiManager.setWifiEnabled(true);
							logWrite(true, 0);
							
							Log.i("carpedm20", "~~~~~~~~~~~~~~~~~~~WIFI ON~~~~~~~~~~~~~~~~~~~");
						}
					}
				}
			}
				
			boolean write = false;

			State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
			while (wifi == NetworkInfo.State.CONNECTING) {
				wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
			}
			
			try {
				//int current_MBC_decision = -1;
				MBC_SKIP = false;
				
				if (!is_MBC_first) {
					//current_MBC_decision = MBC_decision.get(MBC_decision.size() - 1);
					//current_MBC_decision = previous_MBC_decision;
				} else if (MBCMode && current_MBC_decision == 0) {
					MBC_SKIP = true;
					//Log.i("carpedm20", "========================SKIP 0========================");
				}
				
				if (!MBC_SKIP) {
					final URLConnection cn = new URL(mediaUrl).openConnection();
			        cn.setConnectTimeout(3 * 1000);
			        cn.setReadTimeout(5 * 1000);
			    	cn.setRequestProperty("Range","bytes=" + totalBytesRead + "-" + (current_offset + DOWNLOAD_SIZE - 1));
	
			    	ConnectivityManager connectivityManager 
		    		      = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		    		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		    		
			    	if (!(activeNetworkInfo != null && activeNetworkInfo.isConnected())) {
			    		loop_index -= 1;
			    		//Log.i("carpedm20", "0. Network error: " + loop_index + "/" + loop_count);
			    		continue;
			    	} else {
			    		//Log.i("carpedm20", "0. Network sucess: " + loop_index + "/" + loop_count);
			    	}
			    	
			        cn.connect();
			        //Log.i("carpedm20", "1. CONNECT: " + mediaUrl);
			        
			        final InputStream stream = cn.getInputStream();
			        //Log.i("carpedm20", "2. SUCESS: " + mediaUrl);
			        
			        if (stream == null) {
			        	Log.i("carpedm20", "E. InputStream null");
			        	Log.e(getClass().getName(), "Unable to create InputStream for mediaUrl:" + mediaUrl);
			        }
			        
			        //byte buf[] = new byte[16384];
			        byte buf[] = new byte[16384];
			        
			        // download video as much as chunk size
			        do {
			            int numread = stream.read(buf);
			            if (numread <= 0) {
			            	// Log.i("carpedm20", "numread 0");
			                break;
			            }
			            //out.write(buf, 0, numread);
			            Log.i("tunz","Write: "+String.valueOf(loop_index));
			            write = true;
			            current_offset += numread;
			            
			            totalBytesRead += numread;
			            totalKbRead = totalBytesRead/1024;
			            testMediaBuffer(); // after downloading minimum size of video, start media player
			        } while (validateNotInterrupted());
			        //if (validateNotInterrupted()) {
				    //   	fireDataFullyLoaded();
			        //}
			        if (loop_index == 0 && (youtubeMode || MBCMode))
			        	DOWNLOAD_SIZE = TMP_DOWNLOAD_SIZE;
			        
			        stream.close();
				}
			} catch (Exception e) {
				if (write == false)
				{
					Log.e("tunz","Exception!!!!!!!!!!");
					loop_index--;
				}
			}
			
			String batteryStatus = String.valueOf(batteryLevel);
			String wifiStatus = "0";
			
			wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
			while (wifi == NetworkInfo.State.CONNECTING) {
				wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
			}
			
			if(wifi == NetworkInfo.State.CONNECTED) // || wifi == NetworkInfo.State.CONNECTING)
				wifiStatus = "1";
			String message = String.valueOf((System.currentTimeMillis() - start_time)/1000.0) + " " + String.valueOf(totalBytesRead) + " " + wifiStatus + " " + batteryStatus + "\n";
			logOut.write(message.getBytes());
		}
	    
	    Log.i("playing", "finished");
	    downloadFinished = true;
    }  

    public boolean onDataConnectionStateChanged(int state) {
        switch (state) {
        case TelephonyManager.DATA_DISCONNECTED:
        	//Log.d(TAG, "Disconnected");
        	return false;
        case TelephonyManager.DATA_CONNECTED:
            //Log.d(TAG, "Connected");
            return true;
        case TelephonyManager.DATA_CONNECTING:
            //Log.d(TAG, "Connecting");
        	return false;
        case TelephonyManager.DATA_SUSPENDED:
            //Log.d(TAG, "Disconnecting");
        	return false;
        }
        
        return false;
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        
    	if(!mWifi.isConnected()) {
		    TelephonyManager telephonyManager;
			telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			int data_state = telephonyManager.getDataState();
			
			switch (data_state) {
	        case TelephonyManager.DATA_DISCONNECTED:
	        	Log.i("carpedm20", "0. Telephony failed ");
	        	return false;
	        case TelephonyManager.DATA_CONNECTED:
	        	Log.i("carpedm20", "0. Telephony sucess ");
	            return true;
	        case TelephonyManager.DATA_CONNECTING:
	        	Log.i("carpedm20", "0. Telephony failed ");
	        	return false;
	        case TelephonyManager.DATA_SUSPENDED:
	        	Log.i("carpedm20", "0. Telephony failed ");
	        	return false;
	        }
    	} else {
        	NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    		Log.i("carpedm20", "0. WIFI " + activeNetworkInfo.isConnected());
            return activeNetworkInfo != null && activeNetworkInfo.	isConnected();
    	}
    	
		return false;
    }

    private boolean validateNotInterrupted() {
		if (isInterrupted) {
			if (mediaPlayer != null) {
				//mediaPlayer.pause();
			}
			return false;
		} else {
			return true;
		}
    }

    
    /*
     * Test whether we need to transfer buffered data to the MediaPlayer.
     * Interacting with MediaPlayer on non-main UI thread can causes crashes to so perform this using a Handler.
     */
    private void testMediaBuffer() {
	    Runnable updater = new Runnable() {
	        public void run() {
	            if (mediaPlayer == null) {
	            	//  Only create the MediaPlayer once we have the minimum buffered data
	            	if ( totalKbRead >= INITIAL_KB_BUFFER) {
	            		try {
	            			//Log.i("carpedm20", "START");
		            		startMediaPlayer();
	            		} catch (Exception e) {
	            			Log.e(getClass().getName(), "Error copying buffered conent.", e);    			
	            		}
	            	}
	            }
	        }
	    };
	    handler.post(updater);
    }
    
    private void startMediaPlayer() {
        mediaPlayer = new MediaPlayer();
		//mediaPlayer = MediaPlayer.create(context, Uri.parse("http://localhost:8888/data/data/com.example.buffertest/cache/downloadingMedia.dat"));
		Log.i("tunz2","StartMediaPlayer");
        isPlaying = true;
		player_start_time = System.currentTimeMillis()+5000;
		startPlayProgressUpdater();
		startTextUpdater();
		/*
		mediaPlayer.setOnErrorListener(
			new MediaPlayer.OnErrorListener() {
		        public boolean onError(MediaPlayer mp, int what, int extra) {
		        	Log.e(getClass().getName(), "Error in MediaPlayer: (" + what +") with extra (" +extra +")" );
		    		return false;
		        }
		    });
		
		mediaPlayer.setOnPreparedListener(this);
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mediaPlayer.setDisplay(holder);
		mediaPlayer.setVolume(1f, 1f);
		// mediaPlayer.setDataSource("http://localhost:8888/data/data/com.example.buffertest/cache/downloadingMedia.dat");
		mediaPlayer.prepareAsync();
    	*/
    }
    
    private void fireDataFullyLoaded() {
		Runnable updater = new Runnable() { 
			public void run() {
	        	//textStreamed.setText(("Audio full loaded: " + totalKbRead + " Kb read"));
	        }
	    };
	    handler.post(updater);
    }
    
    public MediaPlayer getMediaPlayer() {
    	return mediaPlayer;
	}
	
    // process bar gauge update
    public void startPlayProgressUpdater() {
    	//float progress = (((float)mediaPlayer.getCurrentPosition()/1000)/mediaLengthInSeconds);
    	float progress = (((float)( System.currentTimeMillis() - player_start_time )/1000)/mediaLengthInSeconds);
    	progressBar.setProgress((int)(progress*100));
    	
		//if (mediaPlayer.isPlaying() && isPlaying) {
    	if (isPlaying) {
			Runnable notification = new Runnable() {
		        public void run() {
		        	if(isPlaying) {
		        		startPlayProgressUpdater();
		        	}
				}
		    };
		    handler.postDelayed(notification,1000);
    	}
    }
    
    public void startTextUpdater() {
    	//currentPosition = mediaPlayer.getCurrentPosition();
    	currentPosition = (System.currentTimeMillis() - player_start_time );
    	
    	long previous_expectedBufferTime = expectedBufferTime;
    	//expectedBufferTime = (long)(mediaPlayer.getDuration()*(totalBytesRead - headerSize)/(double)(mediaLengthInByte - headerSize));
    	expectedBufferTime = (long)(( mediaLengthInSeconds*1000 )*(totalBytesRead - headerSize)/(double)(mediaLengthInByte - headerSize));

    	Log.i("carpedm20", "=============expectedBufferTime " + previous_expectedBufferTime);
		Log.i("carpedm20", "=============expectedBufferTime " + expectedBufferTime);
		
    	if (((int)expectedBufferTime/1000) > 10000)
    		expectedBufferTime = previous_expectedBufferTime;
    	
		
    	String mode = "";
    	if (youtubeMode)
    		mode = "Youtube";
    	else if (vimeoMode)
    		mode = "Vimeo";
    	else if (MBCMode) {
    		mode = "MBC";
    		
    		if (climb) {
    			mode += " [Climb] ";
    		} else {
    			mode += " [Stay] ";
    		}
    		
    		mode += Double.valueOf(temp_churn_prob);
    	}
    	String ssid = "none";
    	WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    	WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    	if (WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState()) == NetworkInfo.DetailedState.CONNECTED) {
    		ssid = wifiInfo.getSSID();
    	}
    	
    	ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		
		if (wifi != previous_wifi) {
			if (wifi == State.CONNECTED)
				logWrite(true, 1);
			else
				logWrite(true, 0);
		}
		
		previous_wifi = wifi;
		
    	textStreamed.setText((mode+"\n"+(int)currentPosition/1000)+"sec / " + 
    				((int)expectedBufferTime/1000) +
    				"sec \nBuffer "+((totalBytesRead - headerSize)/1024)+" KB / Max "+
    				(maxBufferSize/1024)+" KB\n"
    				//+ "DP : " + current_MBC_decision + " CP : " + current_MBC_decision\
    				+ "loop_index : " + loop_index + "/" + loop_count + ", MBC_SKIP : " + MBC_SKIP
    				+ ", Connected : "+String.valueOf(wifi));
    	//+" , WiFi : "+ssid +", "
    	
  		//if (mediaPlayer.isPlaying() && isPlaying) {
    	if (isPlaying) {
			Runnable notification = new Runnable() {
		        public void run() {
		        	if(isPlaying) {
		        		startTextUpdater();
		        	}
				}
		    };
		    handler.postDelayed(notification,100);
    	}
    }
    
    public void interrupt() {
    	playButton.setEnabled(false);
    	isInterrupted = true;
    	validateNotInterrupted();
    }

	@Override
	public void onPrepared(MediaPlayer mp) {
		if(isPlaying) {
			// TODO Auto-generated method stub
			mp.start();
			startPlayProgressUpdater();
			startTextUpdater();
			// playButton.setEnabled(true);
		} else {
			playButton.setEnabled(true);
		}
	}
	
	private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			
			int level= intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
			batteryLevel = level;
		}
	};
}