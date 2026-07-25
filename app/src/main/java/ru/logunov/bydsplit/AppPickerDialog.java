package ru.logunov.bydsplit;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class AppPickerDialog {
    private AppPickerDialog() {
    }

    static void show(Context context, List<AppEntry> apps, Consumer<AppEntry> onSelected) {
        int padding = dp(context, 20);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);

        EditText search = new EditText(context);
        search.setHint(R.string.search_apps);
        search.setSingleLine(true);
        search.setTextColor(Color.rgb(30, 35, 42));
        search.setHintTextColor(Color.rgb(110, 118, 130));
        content.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 56)
        ));

        ListView list = new ListView(context);
        AppListAdapter adapter = new AppListAdapter(context, apps);
        list.setAdapter(adapter);
        list.setDivider(new ColorDrawable(Color.rgb(225, 228, 232)));
        list.setDividerHeight(1);
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.choose_app)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry selected = adapter.getItem(position);
            dialog.dismiss();
            onSelected.accept(selected);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(dp(context, 620), dp(context, 620));
            }
        });
        dialog.show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class AppListAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppEntry> source;
        private final List<AppEntry> visible = new ArrayList<>();

        AppListAdapter(Context context, List<AppEntry> source) {
            this.context = context;
            this.source = source;
            this.visible.addAll(source);
        }

        void filter(String query) {
            visible.clear();
            String normalized = query.trim().toLowerCase(Locale.getDefault());
            for (AppEntry entry : source) {
                if (entry.label.toLowerCase(Locale.getDefault()).contains(normalized)
                        || entry.component.getPackageName().toLowerCase(Locale.ROOT)
                        .contains(normalized)) {
                    visible.add(entry);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(context, 12), dp(context, 8),
                        dp(context, 12), dp(context, 8));

                ImageView icon = new ImageView(context);
                icon.setId(android.R.id.icon);
                row.addView(icon, new LinearLayout.LayoutParams(
                        dp(context, 48), dp(context, 48)
                ));

                TextView label = new TextView(context);
                label.setId(android.R.id.text1);
                label.setTextSize(18);
                label.setTextColor(Color.rgb(30, 35, 42));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                );
                labelParams.setMarginStart(dp(context, 16));
                row.addView(label, labelParams);
            }

            AppEntry entry = getItem(position);
            ((ImageView) row.findViewById(android.R.id.icon)).setImageDrawable(entry.icon);
            ((TextView) row.findViewById(android.R.id.text1)).setText(entry.label);
            return row;
        }
    }
}
