package com.example.buffertest2;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;


public class MaximumBufferControl extends Activity {
	static double prob_wifi_on = 1/120.0;					// 1/inter-AP time (sec^(-1))
	static final double gamma_MBC = 1;
	static final int tail_duration = 10;				// seconds
	static final double E_tran_max_lte = 1.33;			// Joule per sec
	static final double E_tail_lte = 0.65;				// Joule per sec
	static final double E_tran_max_wifi = 0.897;		// Joule per sec
	static final double max_rate_lte = 89.97;		// Mbps
	static final double max_rate_wifi = 14.43;		// Mbps 
	static final int video_length = 600;				// seconds
	
	public static double[] churn_time_array = { 0.01, 0.0125, 0.015, 0.0175, 0.02, 
		0.025, 0.03, 0.04, 0.045, 0.05, 
		0.06, 0.0625, 0.0650, 0.0675, 0.07, 
		0.0733, 0.0767, 0.08, 0.095, 0.0975, 
		0.1, 0.11, 0.12, 0.125, 0.13, 
		0.14, 0.16, 0.175, 0.18, 0.19, 
		0.2, 0.25, 0.26, 0.28, 1, 
		1, 1, 1, 1, 1, 
		1, 1, 1, 1, 1, 
		1, 1, 1, 1, 1};
		
	Context main_context;
	
	MaximumBufferControl(Context context) {
		this.main_context = context;
	}
	
	/*
	double getMaxRateLTE() {
		TelephonyManager telephonyManager;
		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		//telephonyManager.getLinkSpeed();
		}
	Integer getWifiRate() {
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		
		Integer linkSpeed = -1;
		
		if (wifiInfo != null) {
			// it is not actual wifi speed
			linkSpeed = wifiInfo.getLinkSpeed(); //measured using WifiInfo.LINK_SPEED_UNITS
		}
		return linkSpeed;
	}
	*/
	
	static int remaining_time(int t)
	{	
		for (int i=0; i < churn_time_array.length; i++)
			if (churn_time_array[i] >= (double)t / (double)video_length)
				return churn_time_array.length - i;
		
		return 0;	
	}
	
	static int not_yet_received_time(int t)
	{	
		for (int i=0; i < churn_time_array.length; i++)
			if (churn_time_array[i] > (double)t / (double)video_length)
				return churn_time_array.length - i;
		
		return 0;
	}
	
	static boolean calcMaximumBuffer_isStop(long received_size, int t) {
		long playback_rate = MainActivity.sharedSetting.getLong("video_bitrate", StreamingMediaPlayer.base_bitrate);
		
		int received_duration = (int) (received_size / (double)(playback_rate*1024/8));
		
		int temp_denominator = remaining_time(t);
		int temp_numerator = temp_denominator - not_yet_received_time(received_duration);
		
		double temp_churning_prob = 0;
		try{
			temp_churning_prob = (double)temp_numerator / (double)temp_denominator;
			
			// x^(-(base_time)*0.7)=0.8
			// temp_churning_prob = 1 - Math.pow(StreamingMediaPlayer.base_x, -t);
			// StreamingMediaPlayer.temp_churn_prob = temp_churning_prob;
		} catch (Exception e)
		{
			return false;
		}
		
		double temp_lambda = prob_wifi_on;
		
		int isErrorReceivedDuration = -1;
		
		if(received_duration - t < 0) {
		    isErrorReceivedDuration = 1;
		}
		
		double temp_wifi_prob = 1 - Math.exp(- temp_lambda * (received_duration - t)); // 0.393
		Log.i("tunz","temp_wifi_prob: "+String.valueOf(temp_wifi_prob));

		double data_waste_cost = temp_churning_prob * (max_rate_lte + gamma_MBC * E_tran_max_lte);
		double offloading_failure_cost = (1 - temp_churning_prob) * temp_wifi_prob * ((max_rate_lte + gamma_MBC * (E_tran_max_lte - E_tran_max_wifi * max_rate_lte / max_rate_wifi)));

		double tail_energy_gain = (1 - temp_churning_prob) * (1 - temp_wifi_prob) * (tail_duration * E_tail_lte) * gamma_MBC;
	
		// 1.0092 // 1.0092 // 1.000092
		Log.i("tunz","data_waste_cost: "+String.valueOf(data_waste_cost)); // 14 // 1.84 // 0.2
		Log.i("tunz","offloading_failure_cost: "+String.valueOf(offloading_failure_cost)); // 0.39 // 3.13 // 4.33
		Log.i("tunz","tail_energy_gain: "+String.valueOf(tail_energy_gain)); // 0.5 // 4.06 // 4.14
				
		if (data_waste_cost + offloading_failure_cost > tail_energy_gain)
			return true;
		else
			return false;
	}
}
