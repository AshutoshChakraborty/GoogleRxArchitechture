/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.tasks;

import android.content.Intent;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Display a grid of {@link Task}s. User can choose to view all, active or completed tasks.
 */
public class TasksFragment extends Fragment {

    private static final String TAG = TasksFragment.class.getSimpleName();

    private TasksViewModel mViewModel;

    private TasksAdapter mListAdapter;

    private View mNoTasksView;

    private ImageView mNoTaskIcon;

    private TextView mNoTaskMainView;

    private TextView mNoTaskAddView;

    private LinearLayout mTasksView;

    private TextView mFilteringLabelView;

    private CompositeSubscription mSubscription = new CompositeSubscription();

    public TasksFragment() {
        // Requires empty public constructor
    }

    public static TasksFragment newInstance() {
        return new TasksFragment();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mViewModel.handleActivityResult(requestCode, resultCode);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mListAdapter = new TasksAdapter(new ArrayList<>(0));

        View root = inflater.inflate(R.layout.tasks_frag, container, false);

        // Set up tasks view
        ListView listView = root.findViewById(R.id.tasks_list);
        listView.setAdapter(mListAdapter);
        mFilteringLabelView = root.findViewById(R.id.filteringLabel);
        mTasksView = root.findViewById(R.id.tasksLL);

        setupNoTasksView(root);
        setupFabButton();
        setupSwipeRefreshLayout(root, listView);

        setHasOptionsMenu(true);

        mViewModel = TasksModule.createTasksViewModel(getActivity());
        mViewModel.restoreState(savedInstanceState);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        bindViewModel();
    }

    @Override
    public void onPause() {
        unbindViewModel();
        super.onPause();
    }

    private void bindViewModel() {
        // using a CompositeSubscription to gather all the subscriptions, so all of them can be
        // later unsubscribed together
        mSubscription = new CompositeSubscription();

        // The ViewModel holds an observable containing the state of the UI.
        // subscribe to the emissions of the Ui Model
        // update the view at every emission fo the Ui Model
        mSubscription.add(mViewModel.getUiModel()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        //onNext
                        this::updateView,
                        //onError
                        error -> Log.e(TAG, "Error loading tasks", error)
                ));

        // subscribe to the emissions of the snackbar text
        // every time the snackbar text emits, show the snackbar
        mSubscription.add(mViewModel.getSnackbarMessage()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        //onNext
                        this::showSnackbar,
                        //onError
                        error -> Log.d(TAG, "Error showing snackbar", error)
                ));

        // subscribe to the emissions of the loading indicator visibility
        // for every emission, update the visibility of the loading indicator
        mSubscription.add(mViewModel.getLoadingIndicatorVisibility()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        //onNext
                        this::setLoadingIndicatorVisibility,
                        //onError
                        error -> Log.d(TAG, "Error showing loading indicator", error)
                ));
    }

    private void unbindViewModel() {
        // unsubscribing from all the subscriptions to ensure we don't have any memory leaks
        mSubscription.unsubscribe();
    }

    private void updateView(TasksUiModel model) {
        int tasksListVisiblity = model.isTasksListVisible() ? View.VISIBLE : View.GONE;
        int noTasksViewVisibility = model.isNoTasksViewVisible() ? View.VISIBLE : View.GONE;
        mTasksView.setVisibility(tasksListVisiblity);
        mNoTasksView.setVisibility(noTasksViewVisibility);

        if (model.isTasksListVisible()) {
            showTasks(model.getItemList());
        }
        if (model.isNoTasksViewVisible() && model.getNoTasksModel() != null) {
            showNoTasks(model.getNoTasksModel());
        }

        setFilterLabel(model.getFilterResId());
    }

    private void setupNoTasksView(View root) {
        mNoTasksView = root.findViewById(R.id.noTasks);
        mNoTaskIcon = root.findViewById(R.id.noTasksIcon);
        mNoTaskMainView = root.findViewById(R.id.noTasksMain);
        mNoTaskAddView = root.findViewById(R.id.noTasksAdd);
        mNoTaskAddView.setOnClickListener(__ -> mViewModel.addNewTask());
    }

    private void setupSwipeRefreshLayout(View root, ListView listView) {
        final ScrollChildSwipeRefreshLayout swipeRefreshLayout =
                root.findViewById(R.id.refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(
                ContextCompat.getColor(getActivity(), R.color.colorPrimary),
                ContextCompat.getColor(getActivity(), R.color.colorAccent),
                ContextCompat.getColor(getActivity(), R.color.colorPrimaryDark)
        );
        // Set the scrolling view in the custom SwipeRefreshLayout.
        swipeRefreshLayout.setScrollUpChild(listView);

        swipeRefreshLayout.setOnRefreshListener(this::forceUpdate);
    }

    private void setupFabButton() {
        FloatingActionButton fab = getActivity().findViewById(R.id.fab_add_task);

        fab.setImageResource(R.drawable.ic_add);
        fab.setOnClickListener(__ -> mViewModel.addNewTask());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putAll(mViewModel.getStateToSave());
        super.onSaveInstanceState(outState);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear:
                clearCompletedTasks();
                break;
            case R.id.menu_filter:
                showFilteringPopUpMenu();
                break;
            case R.id.menu_refresh:
                forceUpdate();
                break;
        }
        return true;
    }

    private void clearCompletedTasks() {
        mSubscription.add(mViewModel.clearCompletedTasks()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        //onCompleted
                        () -> {
                            // nothing to do here
                        },
                        //onError
                        error -> Log.d(TAG, "Error clearing completed tasks", error)
                ));
    }

    private void forceUpdate() {
        mSubscription.add(mViewModel.forceUpdateTasks()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        //onCompleted
                        () -> {
                            // nothing to do here
                        },
                        //onError
                        error -> Log.d(TAG, "Error refreshing tasks", error)
                ));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.tasks_fragment_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void showFilteringPopUpMenu() {
        PopupMenu popup = new PopupMenu(getContext(), getActivity().findViewById(R.id.menu_filter));
        popup.getMenuInflater().inflate(R.menu.filter_tasks, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.active:
                    mViewModel.filter(TasksFilterType.ACTIVE_TASKS);
                    break;
                case R.id.completed:
                    mViewModel.filter(TasksFilterType.COMPLETED_TASKS);
                    break;
                default:
                    mViewModel.filter(TasksFilterType.ALL_TASKS);
                    break;
            }
            return true;
        });

        popup.show();
    }

    private void setLoadingIndicatorVisibility(final boolean isVisible) {
        if (getView() == null) {
            return;
        }
        final SwipeRefreshLayout srl = getView().findViewById(R.id.refresh_layout);
        // Make sure setRefreshing() is called after the layout is done with everything else.
        srl.post(() -> srl.setRefreshing(isVisible));
    }

    private void showTasks(List<TaskItem> tasks) {
        mListAdapter.replaceData(tasks);
    }

    private void showNoTasks(NoTasksModel model) {
        mNoTaskMainView.setText(model.getText());
        mNoTaskIcon.setImageResource(model.getIcon());
        mNoTaskAddView.setVisibility(model.isAddNewTaskVisible() ? View.VISIBLE : View.GONE);

    }

    private void setFilterLabel(@StringRes int text) {
        mFilteringLabelView.setText(text);
    }

    private void showSnackbar(@StringRes int message) {
        Snackbar.make(getView(), message, Snackbar.LENGTH_LONG).show();
    }
}
