package com.deepseekharness.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import com.deepseekharness.app.BuildConfig;
import com.deepseekharness.app.R;
import com.deepseekharness.app.core.UpdateRepository;
import com.deepseekharness.app.util.UpdatePolicy;

/** 更新由用户选择通道和确认安装；下载任务不依赖页面生命周期。 */
public final class UpdateActivity extends AppCompatActivity {
    private UpdateRepository repository;
    private boolean resumeInstall;
    private boolean installDispatchReady;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); setContentView(R.layout.activity_update);
        repository = new ViewModelProvider(this).get(UpdateRepository.class);
        resumeInstall = saved != null && saved.getBoolean("resumeInstall");
        repository.restoreInterruptedInstall(saved != null && saved.getBoolean("installPending"));
        ((TextView)findViewById(R.id.update_current)).setText(BuildConfig.VERSION_NAME);
        ((TextView)findViewById(R.id.update_code)).setText(String.valueOf(BuildConfig.VERSION_CODE));
        ((TextView)findViewById(R.id.update_edition)).setText(BuildConfig.LOW_ANDROID?com.deepseekharness.app.util.UiText.choose("兼容版","Compatibility"):com.deepseekharness.app.util.UiText.choose("标准版","Standard"));
        RadioGroup channels = findViewById(R.id.update_channels);
        channels.check(UpdatePolicy.PREVIEW.equals(repository.channel()) ? R.id.update_preview : R.id.update_stable);
        channels.setOnCheckedChangeListener((g, id) -> repository.setChannel(id == R.id.update_preview ? UpdatePolicy.PREVIEW : UpdatePolicy.STABLE));
        DshaSelectView channel=findViewById(R.id.update_channel_choice);channel.setPrompt(getString(R.string.ui2_update_channel));
        channel.setAdapter(new android.widget.ArrayAdapter<>(this,R.layout.item_data_choice,new String[]{getString(R.string.ui_m0173),getString(R.string.ui_m0215)}));channel.setSelection(UpdatePolicy.PREVIEW.equals(repository.channel())?1:0);
        channel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){channels.check(position==1?R.id.update_preview:R.id.update_stable);}});
        findViewById(R.id.update_back).setOnClickListener(v -> finish());
        ((TextView)findViewById(R.id.update_dsh)).setText(com.deepseekharness.app.util.Constants.DSH_VERSION);
        findViewById(R.id.update_runtime).setOnClickListener(v->showRuntimePlan());
        findViewById(R.id.update_runtime_rollback).setOnClickListener(v->RuntimeRecoveryUi.show(this));

        findViewById(R.id.update_changelog).setOnClickListener(v->CardSheet.show(this,getString(R.string.ui134_changelog),com.deepseekharness.app.util.UiText.choose("DSHA 0.2.1-rc2（测试包）\n\n• DSH 运行时由 0.1.7-rc.2 升级到 0.2.1-rc.2：新增若干前端包，会话格式带 v0→v4 迁移链。\n• UI 保持内置 dsh-web-mobile 3.0.3，仅对齐依赖声明。\n• 修复虚拟屏：内置 vscreen 工具此前补丁清单为空，插件从未装载，android_vscreen_* 全部不可用；现已补齐。\n• 客户端适配补丁逐条重新锚定到 0.2.1；上游已自行修掉的部分退役。\n• 测试包使用测试签名，不能覆盖安装正式版；升级前请自行备份数据。", "DSHA 0.2.1-rc2 (test build)\n\n• DSH runtime upgraded 0.1.7-rc.2 -> 0.2.1-rc.2 (session format v0->v4 migration chain).\n• Mobile UI stays on the bundled dsh-web-mobile 3.0.3; only dependency declarations were aligned.\n• Virtual screen fixed: the bundled vscreen plugin had an empty patch manifest and was never loaded.\n• Client patches re-anchored to 0.2.1; retired where upstream already fixed them.\n• Test build signed with a test key: it cannot overwrite the official app. Back up your data first."));
        super.onSaveInstanceState(saved);
    }
    @Override protected void onResume() {
        super.onResume();
        if (resumeInstall) {
            resumeInstall = false;
            if (android.os.Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) install();
            else Toast.makeText(this, com.deepseekharness.app.util.UiText.text("未允许安装更新，可稍后重试"), Toast.LENGTH_SHORT).show();
        }
    }
    @Override protected void onPostResume() {
        super.onPostResume();
        installDispatchReady = true;
        dispatchInstall();
    }
    @Override protected void onPause() {
        installDispatchReady = false;
        super.onPause();
    }
}
