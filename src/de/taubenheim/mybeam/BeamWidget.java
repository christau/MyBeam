/**
 * Created: 12.06.2010
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

/**
 * @author tiger
 * 
 */
public class BeamWidget extends AppWidgetProvider
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		super.onReceive(context, intent);
		if ("de.taubenheim.intent.action.STATUS_CHANGED".equals(intent.getAction()))
		{
			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, BeamWidget.class));
			boolean state = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("de.taubenheim.mybeam.state", false);
			showWidget(context, manager, widgetIds, state);

		} else if ("de.taubenheim.intent.action.STATUS_TOGGLED".equals(intent.getAction()))
		{
			try
			{
				boolean state = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("de.taubenheim.mybeam.state", false);
				if (!state)
				{
					PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("de.taubenheim.mybeam.state", true).commit();
					int res = runScriptAsRoot(context, "echo 1 > /sys/devices/platform/flashlight.0/leds/flashlight/brightness", new StringBuilder(), 10000);
					Intent i = new Intent();
					i.setClassName("de.taubenheim.mybeam", BeamService.class.getName());
					context.startService(i);
				} else
				{
					PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("de.taubenheim.mybeam.state", false).commit();
					runScriptAsRoot(context, "echo 0 > /sys/devices/platform/flashlight.0/leds/flashlight/brightness", new StringBuilder(), 10000);
					Intent i = new Intent();
					i.setClassName("de.taubenheim.mybeam", BeamService.class.getName());
					context.stopService(i);
				}
				AppWidgetManager manager = AppWidgetManager.getInstance(context);
				int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, BeamWidget.class));
				showWidget(context, manager, widgetIds, !state);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] ints)
	{
		super.onUpdate(context, appWidgetManager, ints);

		boolean state = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("de.taubenheim.mybeam.state", false);
		showWidget(context, appWidgetManager, ints, state);
	}

	private void showWidget(Context context, AppWidgetManager manager, int[] widgetIds, boolean state)
	{
		RemoteViews views = createRemoteViews(context, state);
		manager.updateAppWidget(widgetIds, views);
	}

	private RemoteViews createRemoteViews(Context context, boolean state)
	{
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.main);
		int iconId = state ? R.drawable.beam_on : R.drawable.beam_off;
		views.setImageViewResource(R.id.Button01, iconId);
		Intent msg = new Intent("de.taubenheim.intent.action.STATUS_TOGGLED");
		PendingIntent intent = PendingIntent.getBroadcast(context, -1 /* not used */, msg, PendingIntent.FLAG_UPDATE_CURRENT);
		views.setOnClickPendingIntent(R.id.Button01, intent);
		return views;
	}
	
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res, final long timeout)
	{
		final File file = new File(ctx.getCacheDir(), "mybeam.sh");
		final ScriptRunner runner = new ScriptRunner(file, script, res);
		runner.start();
		try
		{
			if (timeout > 0)
			{
				runner.join(timeout);
			} else
			{
				runner.join();
			}
			if (runner.isAlive())
			{
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex)
		{
		}
		return runner.exitcode;
	}

	/**
	 * This class is taken from the project droidwall  http://droidwall.googlecode.com/
	 *
	 */
	private static final class ScriptRunner extends Thread
	{
		private final File file;
		private final String script;
		private final StringBuilder res;
		public int exitcode = -1;
		private Process exec;

		/**
		 * Creates a new script runner.
		 * 
		 * @param file
		 *                temporary script file
		 * @param script
		 *                script to run
		 * @param res
		 *                response output
		 */
		public ScriptRunner(File file, String script, StringBuilder res)
		{
			this.file = file;
			this.script = script;
			this.res = res;
		}

		@Override
		public void run()
		{
			try
			{
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 " + abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				out.write(script);
				if (!script.endsWith("\n"))
					out.write("\n");
				out.write("exit\n");
				out.close();
				// Create the "su" request to run the script
				exec = Runtime.getRuntime().exec(abspath);
//				exec = Runtime.getRuntime().exec("su -c " + abspath);
				InputStreamReader r = new InputStreamReader(exec.getInputStream());
				final char buf[] = new char[1024];
				int read = 0;
				// Consume the "stdout"
				while ((read = r.read(buf)) != -1)
				{
					if (res != null)
						res.append(buf, 0, read);
				}
				// Consume the "stderr"
				r = new InputStreamReader(exec.getErrorStream());
				read = 0;
				while ((read = r.read(buf)) != -1)
				{
					if (res != null)
						res.append(buf, 0, read);
				}
				// get the process exit code
				if (exec != null)
					this.exitcode = exec.waitFor();
			} catch (InterruptedException ex)
			{
				if (res != null)
					res.append("\nOperation timed-out");
			} catch (Exception ex)
			{
				if (res != null)
					res.append("\n" + ex);
			} finally
			{
				destroy();
			}
		}

		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy()
		{
			if (exec != null)
				exec.destroy();
			exec = null;
		}
	}

}
