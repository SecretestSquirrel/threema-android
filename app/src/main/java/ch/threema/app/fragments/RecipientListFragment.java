/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.ListFragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.SnackbarUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public abstract class RecipientListFragment extends ListFragment implements ListView.OnItemLongClickListener {
	public static final String ARGUMENT_MULTI_SELECT = "ms";

	protected ContactService contactService;
	protected GroupService groupService;
	protected DistributionListService distributionListService;
	protected ConversationService conversationService;
	protected PreferenceService preferenceService;
	protected IdListService blacklistService;
	protected DeadlineListService hiddenChatsListService;
	protected FragmentActivity activity;
	protected Parcelable listInstanceState;
	protected FloatingActionButton floatingActionButton;
	protected Snackbar snackbar;
	protected ProgressBar progressBar;
	protected View topLayout;
	protected boolean multiSelect = true;
	protected FilterableListAdapter adapter;

	private boolean isVisible = false;
	private static long selectionTime = 0;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		activity = getActivity();

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		try {
			contactService = serviceManager.getContactService();
			groupService = serviceManager.getGroupService();
			distributionListService = serviceManager.getDistributionListService();
			blacklistService = serviceManager.getBlackListService();
			conversationService = serviceManager.getConversationService();
			preferenceService = serviceManager.getPreferenceService();
			hiddenChatsListService = serviceManager.getHiddenChatsListService();
		} catch (ThreemaException e) {
			LogUtil.exception(e, activity);
			return null;
		}

		Bundle bundle = getArguments();
		if (bundle != null) {
			multiSelect = bundle.getBoolean(ARGUMENT_MULTI_SELECT, true);
		}

		topLayout = inflater.inflate(R.layout.fragment_list, container, false);
		return topLayout;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		ArrayList<Integer> checkedItemPositions = null;

		// recover after rotation
		if (savedInstanceState != null) {
			this.listInstanceState = savedInstanceState.getParcelable(getBundleName());
			checkedItemPositions = savedInstanceState.getIntegerArrayList(getBundleName() + "c");
		}

		createListAdapter(checkedItemPositions);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		getListView().setDividerHeight(0);
		getListView().setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

		if (!multiSelect && getAddText() != 0) {
			View headerView = View.inflate(activity, R.layout.header_recipient_list, null);
			((ImageView)headerView.findViewById(R.id.avatar_view)).setImageResource(getAddIcon());
			((TextView)headerView.findViewById(R.id.text_view)).setText(getAddText());
			headerView.findViewById(R.id.container).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = getAddIntent();
					if (intent != null) {
						startActivity(intent);
						activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
					}
				}
			});
			getListView().addHeaderView(headerView);
		}

		progressBar = view.findViewById(R.id.progress);

		floatingActionButton = view.findViewById(R.id.floating);

		if (isMultiSelectAllowed()) {
			getListView().setOnItemLongClickListener(this);
			floatingActionButton.setOnClickListener(new DebouncedOnClickListener(500) {
				@Override
				public void onDebouncedClick(View v) {
					onFloatingActionButtonClick();
				}
			});
		} else {
			floatingActionButton.hide();
		}
	}

	@Override
	public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		if (getListView().getChoiceMode() != AbsListView.CHOICE_MODE_MULTIPLE) {
			if (System.currentTimeMillis() - selectionTime > 500) {
				selectionTime = System.currentTimeMillis();
				getListView().setChoiceMode(AbsListView.CHOICE_MODE_NONE );
				onItemClick(v);
			}
		} else {
			if (adapter.getCheckedItemCount() <= 0) {
				stopMultiSelect();
			} else {
				updateMultiSelect();
			}
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
		startMultiSelect();
		getListView().setItemChecked(position, true);
		if (v instanceof CheckableConstraintLayout) {
			((CheckableConstraintLayout) v).setChecked(true);
		} else {
			((CheckableRelativeLayout) v).setChecked(true);
		}
		updateMultiSelect();

		return true;
	}

	private void startMultiSelect() {
		getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
		if (isVisible) {
			snackbar = SnackbarUtil.make(topLayout, "", Snackbar.LENGTH_INDEFINITE, 4);
			snackbar.setBackgroundTint(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent));
			snackbar.setTextColor(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSecondary));
			snackbar.getView().getLayoutParams().width = AppBarLayout.LayoutParams.MATCH_PARENT;
			snackbar.show();
			snackbar.getView().post(new Runnable() {
				@Override
				public void run() {
					floatingActionButton.show();
				}
			});
		}
	}

	private void updateMultiSelect() {
		if (getListView().getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE) {
			if (getAdapter().getCheckedItemCount() > 0) {
				if (snackbar != null) {
					snackbar.setText(getString(R.string.really_send, getRecipientList()));
				}
			}
		}
	}

	private void stopMultiSelect() {
		getListView().setChoiceMode(AbsListView.CHOICE_MODE_NONE);
		if (snackbar != null && snackbar.isShown()) {
			snackbar.dismiss();
		}
		floatingActionButton.postDelayed(new Runnable() {
			@Override
			public void run() {
				floatingActionButton.hide();
			}
		}, 100);
	}

	private void onItemClick(View v) {
		final Object object = adapter.getClickedItem(v);
		if (object != null) {
			((RecipientListBaseActivity) activity).prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(object)));
		}
	}

	private void onFloatingActionButtonClick() {
		final HashSet<?> objects = adapter.getCheckedItems();
		if (!objects.isEmpty()) {
			((RecipientListBaseActivity) activity).prepareForwardingOrSharing(new ArrayList<>(objects));
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		try {
			ListView listView = getListView();

			if (listView != null) {
				outState.putParcelable(getBundleName(), listView.onSaveInstanceState());
				// save checked items, if any
				if (listView.getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE && getAdapter().getCheckedItemCount() > 0) {
					outState.putIntegerArrayList(getBundleName() + "c", getAdapter().getCheckedItemPositions());
				}
			}
		} catch (Exception e) {
			// getListView may cause IllegalStateException
		}

		super.onSaveInstanceState(outState);
	}

	protected void restoreCheckedItems(ArrayList<Integer> checkedItemPositions) {
		// restore previously checked items
		if (checkedItemPositions != null && checkedItemPositions.size() > 0) {
			startMultiSelect();
			updateMultiSelect();
		}
	}

	private String getRecipientList()  {
		StringBuilder builder = new StringBuilder();

		for (Object model: adapter.getCheckedItems()) {
			String name = null;
			if (model instanceof ContactModel) {
				name = NameUtil.getDisplayNameOrNickname((ContactModel) model, true);
			} else if (model instanceof GroupModel) {
				name = NameUtil.getDisplayName((GroupModel) model, this.groupService);
			} else if (model instanceof DistributionListModel) {
				name = NameUtil.getDisplayName((DistributionListModel) model, this.distributionListService);
			}
			if (name != null) {
				if (builder.length() > 0) {
					builder.append(", ");
				}
				builder.append(name);
			}
		}
		return builder.toString();
	}

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);

		isVisible = isVisibleToUser;

		if (isVisibleToUser) {
			if (isMultiSelectAllowed() && getView() != null) {
				if (getListView().getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE) {
					if (snackbar == null || !snackbar.isShownOrQueued()) {
						startMultiSelect();
						updateMultiSelect();
					}
				}
			}
		}
	}

	public FilterableListAdapter getAdapter() { return adapter; }

	void setListAdapter(FilterableListAdapter adapter) {
		super.setListAdapter(adapter);

		if (isAdded()) {
			try {
				progressBar.setVisibility(View.GONE);

				// add text view if contact list is empty
				EmptyView emptyView = new EmptyView(activity);
				emptyView.setup(getEmptyText());
				((ViewGroup) getListView().getParent()).addView(emptyView);
				getListView().setEmptyView(emptyView);
			} catch (IllegalStateException ignored) {
			}
		}
	}

	protected abstract void createListAdapter(ArrayList<Integer> checkedItems);
	protected abstract String getBundleName();
	protected abstract @StringRes int getEmptyText();
	protected abstract @DrawableRes int getAddIcon();
	protected abstract @StringRes int getAddText();
	protected abstract Intent getAddIntent();
	protected abstract boolean isMultiSelectAllowed();
}
