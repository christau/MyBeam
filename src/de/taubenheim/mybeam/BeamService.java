/**
 * Created: 20.06.2010
 * Copyright 2010 Chris Taubenheim <chris {at} taubenheim.de>
 *  This file is part of MyBeam.

 *  MyBeam is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation, either version 3 of
 *  the License, or (at your option) any later version.

 *  MyBeam is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with MyBeam.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package de.taubenheim.mybeam;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class BeamService extends Service
{

	private BroadcastReceiver m_receiver;

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if(m_receiver == null)
		{
			m_receiver = new BroadcastReceiver()
			{

				@Override
				public void onReceive(Context context, Intent intent)
				{
					boolean state = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("de.taubenheim.mybeam.state", false);
					if(state)
					BeamWidget.runScriptAsRoot(context, "echo 1 > /sys/devices/platform/flashlight.0/leds/flashlight/brightness", new StringBuilder(), 10000);
				}
			};
		}
		
		getApplicationContext().registerReceiver(m_receiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		getApplicationContext().unregisterReceiver(m_receiver);
	}

}
