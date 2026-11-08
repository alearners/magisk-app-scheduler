package com.appscheduler.lsposed;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 极简界面：包名、锁屏密码、时间点、启用开关、立即执行、日志。
 */
public class MainActivity extends Activity {

    private Config config;
    private EditText pkgInput;
    private EditText passwordInput;
    private EditText timesInput;
    private CheckBox enabledBox;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = Config.get(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(root, "定时启动 App · LSPosed 版");

        pkgInput = addInput(root, "目标应用包名（如 com.example.app）", config.pkg(),
                InputType.TYPE_CLASS_TEXT);
        passwordInput = addInput(root, "锁屏密码（纯数字）", config.password(),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        timesInput = addInput(root, "每天执行的时间点，逗号分隔（如 08:00, 20:00）", config.times(),
                InputType.TYPE_CLASS_TEXT);

        enabledBox = new CheckBox(this);
        enabledBox.setText("启用定时任务");
        enabledBox.setChecked(config.enabled());
        root.addView(enabledBox);

        Button save = new Button(this);
        save.setText("保存并应用");
        save.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                saveAndSchedule();
            }
        });
        root.addView(save);

        Button runNow = new Button(this);
        runNow.setText("立即执行一次");
        runNow.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                runNow();
            }
        });
        root.addView(runNow);

        Button refresh = new Button(this);
        refresh.setText("刷新日志");
        refresh.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                logView.setText(LogStore.read(MainActivity.this));
            }
        });
        root.addView(refresh);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setPadding(0, dp(12), 0, 0);
        logView.setText(LogStore.read(this));
        root.addView(logView);

        setContentView(scroll);
    }

    private void saveAndSchedule() {
        String pkg = pkgInput.getText().toString().trim();
        String pwd = passwordInput.getText().toString().trim();
        String times = timesInput.getText().toString().trim();
        boolean enabled = enabledBox.isChecked();

        config.save(pkg, pwd, times, enabled);
        Scheduler.cancel(this);
        Scheduler.scheduleNext(this);
        LogStore.append(this, "配置已保存：" + pkg + " / " + times + " / 启用=" + enabled);
        toast("已保存，下次执行：" + (enabled ? Scheduler.describeNext(this) : "未启用"));
        logView.setText(LogStore.read(this));
    }

    private void runNow() {
        toast("开始执行，请稍候…");
        final Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result;
                try {
                    result = TaskRunner.run(activity.getApplicationContext());
                } catch (Throwable t) {
                    LogStore.append(activity, "执行异常: " + t);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            logView.setText(LogStore.read(activity));
                            toast("执行异常");
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        logView.setText(result);
                        toast("执行完成，详见日志");
                    }
                });
            }
        }, "manual-run").start();
    }

    private void addTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18);
        tv.setPadding(0, 0, 0, dp(12));
        parent.addView(tv);
    }

    private EditText addInput(LinearLayout parent, String hint, String value, int inputType) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setPadding(0, dp(10), 0, dp(4));
        parent.addView(label);

        EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setText(value);
        input.setSingleLine(true);
        parent.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
