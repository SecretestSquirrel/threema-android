/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.preference.PreferenceManager;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.PushUtil;

public class UpdateReceiver extends BroadcastReceiver {
	private static final Logger logger = LoggerFactory.getLogger(UpdateReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
			logger.info("*** App was updated ***");

			// force token register
			PushUtil.clearPushTokenSentDate(context);
		}
	}


}
