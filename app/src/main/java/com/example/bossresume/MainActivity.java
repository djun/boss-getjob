package com.example.bossresume;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_ACCESSIBILITY = 1001;
    private static final int REQUEST_CODE_OVERLAY = 1002;

    private TextView tvStatus;
    private TextView tvLog;
    private Button btnStart;
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
        tvStatus = findViewById(R.id.tv_status);
        
        // 初始化按钮状态 - 默认开始按钮可点击，停止按钮不可点击
        btnStart.setEnabled(true);
        
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
            // 首先请求悬浮窗权限
            requestOverlayPermission();
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
        try {
            // 注销广播接收器
            if (serviceStatusReceiver != null) {
                unregisterReceiver(serviceStatusReceiver);
                serviceStatusReceiver = null;
            }
        } catch (Exception e) {
            // 忽略接收器未注册的异常
            Log.e("MainActivity", "Error unregistering receiver: " + e.getMessage());
        }
    }

    private void startService() {
        // 从自定义关键词输入框或下拉列表获取关键词
        String keywords;
        // 先检查选择的是哪个关键词类别
        String selectedCategory = jobCategorySpinner.getSelectedItem().toString();
        
        if ("自定义关键词".equals(selectedCategory)) {
            // 使用用户输入的关键词
            keywords = etKeywords.getText().toString();
            Log.d(TAG, "使用自定义关键词: " + keywords);
        } else {
            // 使用预设的关键词列表
            keywords = getCategoryKeywords(selectedCategory);
            Log.d(TAG, "使用预设关键词类别: " + selectedCategory + ", 关键词: " + keywords);
        }
        
        // 使用统一的SharedPreferences名称
        SharedPreferences sharedPreferences = getSharedPreferences("boss_resume_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("keywords", keywords);
        editor.putString("selected_category", selectedCategory); // 保存选择的类别
        editor.apply();
        
        Log.d(TAG, "保存用户关键词到SharedPreferences: " + keywords);
        
        // 创建服务启动Intent
        Intent intent = new Intent(this, BossResumeService.class);
        intent.setAction(BossResumeService.ACTION_START);
        startService(intent);
    }

    // 根据类别获取预设的关键词
    private String getCategoryKeywords(String category) {
        switch (category) {
            case "运维":
                return "运维,docker,k8s,系统运维,集群运维,kubernetes,devops,PaaS,应用运维,交付,迁移,K8S,运维开发,云计算,实施,业务运维,SRE,sre,云平台,linux,DevOps,公有云,私有云,基础架构,容器";
            case "Java开发":
                return "高级后端,后端,Java,开发,开发工程师,项目开发,研发,服务端研发,java开发,Java开发,JAVA,后端开发,高级Java";
            case "产品经理":
                return "产品,产品经理,产品,产品专家,数字化专家,软件产品经理,B端产品经理,C端,高级产品经理,AI产品经理,app产品经理";
            case "前端开发":
                return "前端,前端开发,web前端,react,vue,web3,前端工程师,H5,H5开发,TypeScript,资深前端,高级前端,React,小程序,Vue";
            case "测试":
                return "测试,高级测试,系统测试,软件测试,测试工程师,后端测试,功能测试,app测试,自动化测试,业务测试";
            case "销售":
                return "销售,销售专员,销售经理,业务销售,软件销售,销售顾问,电话销售,网络销售,销售代表,大客户销售,客户销售";
            case "运营":
                return "运营专员,运营经理,运营,运营助理,渠道运营,技术运营,抖音运营,独立站运营,产品运营,内容运营,用户运营,活动运营,电商运营,跨境电商运营,新媒体运营,网站运营,社区运营,直播运营,游戏运营,数据运营,车辆运营,策略运营";
            default:
                // 如果没有找到匹配的类别，返回空字符串
                return "";
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
        
        if (isRunning) {
            startButton.setEnabled(false);
            isServiceRunning = true;
        } else {
            startButton.setEnabled(true);
            isServiceRunning = false;
        }
    }

    /**
     * 请求悬浮窗权限
     */
    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
        } else {
            checkAccessibilityServiceEnabled();
        }
    }

    /**
     * 检查无障碍服务是否启用，并相应处理
     */
    private void checkAccessibilityServiceEnabled() {
        if (isAccessibilityServiceEnabled()) {
            startService();
            updateLog("开始自动投递任务");
        } else {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // 悬浮窗权限获取成功，继续检查无障碍服务
                checkAccessibilityServiceEnabled();
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能显示停止按钮", Toast.LENGTH_LONG).show();
            }
        }
    }
} 