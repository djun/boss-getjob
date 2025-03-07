package com.example.bossresume;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.accessibility.AccessibilityManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_SERVICE_STATUS_CHANGED = "com.example.bossresume.ACTION_SERVICE_STATUS_CHANGED";
    public static final String PREF_NAME = "BossResumePrefs";
    public static final String KEY_KEYWORDS = "keywords";

    private TextView tvStatus;
    private TextView tvLog;
    private Button btnStart;
    private Button btnStop;
    private Button btnAccessibilitySettings;
    private EditText etKeywords;
    private SharedPreferences sharedPreferences;
    private Spinner jobCategorySpinner;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        // 显示版本信息
        TextView versionInfoText = findViewById(R.id.versionInfoText);
        String versionInfo = String.format(
            getString(R.string.version_info), 
            getString(R.string.app_version)
        );
        versionInfoText.setText(versionInfo);
        
        // 初始化控件
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        tvStatus = findViewById(R.id.tv_status);
        
        // 初始化按钮状态 - 默认开始按钮可点击，停止按钮不可点击
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        
        // 检查无障碍服务状态并更新UI
        if (isAccessibilityServiceEnabled()) {
            tvStatus.setText("服务状态：已启用");
        } else {
            tvStatus.setText("服务状态：未启用");
        }
        
        tvLog = findViewById(R.id.tv_log);
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings);
        etKeywords = findViewById(R.id.et_keywords);
        jobCategorySpinner = findViewById(R.id.spinner_job_category);

        // 设置职位类别下拉框
        setupJobCategorySpinner();

        // 加载已保存的关键词
        String savedKeywords = sharedPreferences.getString(KEY_KEYWORDS, getString(R.string.default_keywords));
        etKeywords.setText(savedKeywords);

        // 添加文本变化监听器，实现自动保存
        etKeywords.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String keywords = s.toString().trim();
                if (!keywords.isEmpty()) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_KEYWORDS, keywords);
                    editor.apply();
                }
            }
        });

        btnAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnStart.setOnClickListener(v -> {
            if (isAccessibilityServiceEnabled()) {
                startService();
                updateLog("开始自动投递任务");
            } else {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });

        btnStop.setOnClickListener(v -> {
            stopService();
            updateLog("停止自动投递任务");
        });

        registerBroadcastReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (isAccessibilityServiceEnabled()) {
            tvStatus.setText("服务状态：已启用");
        } else {
            tvStatus.setText("服务状态：未启用");
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    public void updateLog(final String message) {
        runOnUiThread(() -> {
            tvLog.append(message + "\n");
        });
    }

    public static void appendLog(Context context, String message) {
        if (context instanceof MainActivity) {
            ((MainActivity) context).updateLog(message);
        }
    }

    // 添加广播接收器定义
    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.bossresume.LOG_UPDATE".equals(intent.getAction())) {
                String logMessage = intent.getStringExtra("log_message");
                if (logMessage != null) {
                    updateLogDisplay(logMessage);
                }
            }
        }
    };

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.bossresume.LOG_UPDATE");
        
        // 修改注册方式，添加 RECEIVER_NOT_EXPORTED 标志
        registerReceiver(
            logReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        );
    }

    // 添加更新日志显示的方法
    private void updateLogDisplay(String message) {
        TextView logTextView = findViewById(R.id.tv_log);
        if (logTextView != null) {
            logTextView.append(message + "\n");
            // 让TextView滚动到底部
            int scrollAmount = logTextView.getLayout().getLineTop(logTextView.getLineCount()) - logTextView.getHeight();
            if (scrollAmount > 0) {
                logTextView.scrollTo(0, scrollAmount);
            }
        }
    }

    private BroadcastReceiver serviceStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SERVICE_STATUS_CHANGED.equals(intent.getAction())) {
                boolean isRunning = intent.getBooleanExtra("running", false);
                int count = intent.getIntExtra("count", 0);
                
                updateUI(isRunning, count);
            }
        }
    };

    private void updateUI(boolean isRunning, int count) {
        updateServiceStatus(isRunning);
        
        tvStatus.setText("服务状态：" + (isRunning ? "运行中" : "未运行"));
        tvLog.append("已投递: " + count + "\n");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(serviceStatusReceiver);
    }

    private void startService() {
        if (!isServiceRunning) {  // 只有在服务未运行时才启动
            Intent intent = new Intent(this, BossResumeService.class);
            intent.setAction(BossResumeService.ACTION_START);
            // 添加关键词到Intent
            intent.putExtra(KEY_KEYWORDS, etKeywords.getText().toString().trim());
            startService(intent);
            updateServiceStatus(true);  // 只在用户点击开始时更新状态
            Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show();
            
            // 启动BOSS直聘APP
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                BossResumeService.launchBossApp(this);
            }, 500);
        }
    }

    private void stopService() {
        if (isServiceRunning) {
            // 停止服务
            Intent intent = new Intent(this, BossResumeService.class);
            stopService(intent);
            
            // 更新UI状态
            updateServiceStatus(false);
            
            Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show();
        }
    }

    // 提供静态方法供BossResumeService获取关键词
    public static String getKeywords(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_KEYWORDS, context.getString(R.string.default_keywords));
    }

    // 设置职位类别下拉框
    private void setupJobCategorySpinner() {
        // 创建适配器
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.job_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        jobCategorySpinner.setAdapter(adapter);

        // 设置选择监听器
        jobCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = parent.getItemAtPosition(position).toString();
                
                // 根据选择的职位类别设置对应的关键词
                if (position == 0) {
                    // 自定义关键词，不做任何更改
                    return;
                }
                
                String keywords;
                switch (selectedCategory) {
                    case "运维":
                        keywords = getString(R.string.keywords_ops);
                        break;
                    case "Java开发":
                        keywords = getString(R.string.keywords_java);
                        break;
                    case "产品经理":
                        keywords = getString(R.string.keywords_pm);
                        break;
                    case "前端开发":
                        keywords = getString(R.string.keywords_frontend);
                        break;
                    case "测试":
                        keywords = getString(R.string.keywords_test);
                        break;
                    case "销售":
                        keywords = getString(R.string.keywords_sales);
                        break;
                    case "运营":
                        keywords = getString(R.string.keywords_operation);
                        break;
                    default:
                        keywords = getString(R.string.default_keywords);
                        break;
                }
                
                // 设置关键词文本框的内容
                etKeywords.setText(keywords);
                
                // 自动保存关键词
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_KEYWORDS, keywords);
                editor.apply();
                
                // 显示提示信息
                Toast.makeText(MainActivity.this, "已设置" + selectedCategory + "类别关键词", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何操作
            }
        });
    }

    private void updateServiceStatus(boolean isRunning) {
        Button startButton = findViewById(R.id.btn_start);
        Button stopButton = findViewById(R.id.btn_stop);
        
        if (isRunning) {
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            isServiceRunning = true;
        } else {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            isServiceRunning = false;
        }
    }
} 