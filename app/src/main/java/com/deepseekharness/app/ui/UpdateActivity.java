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

        findViewById(R.id.update_changelog).setOnClickListener(v->CardSheet.show(this,getString(R.string.ui134_changelog),com.deepseekharness.app.util.UiText.choose("DSHA 0.2.1-rc2（测试包）\n\n• DSH 运行时由 0.1.7-rc.2 升级到 0.2.1-rc.2：客户端 UI 拆包、会话格式带 v0→v4 迁移链。\n• UI 保持内置 dsh-web-mobile 3.0.3，仅对齐依赖声明。\n• 修复虚拟屏：内置 vscreen 工具此前补丁清单为空，插件从未装载，android_vscreen_* 全部不可用；现已补齐。\n• 客户端适配补丁逐条重新锚定到 0.2.1；上游已自行修掉的部分退役。\n• 测试包使用测试签名，不能覆盖安装正式版；升级前请自行备份数据。", "DSHA 0.2.1-rc2 (test build)\n\n• DSH runtime upgraded 0.1.7-rc.2 -> 0.2.1-rc.2 (split client UI packages, session format v0->v4 migration chain).\n• Mobile UI stays on the bundled dsh-web-mobile 3.0.3; only dependency declarations were aligned.\n• Virtual screen fixed: the bundled vscreen plugin had an empty patch manifest and was never loaded.\n• Client patches re-anchored to 0.2.1; retired where upstream already fixed them.\n• Test build signed with a test key: it cannot overwrite the official app. Back up your data first.")));
        findViewById(R.id.update_release_detail).setOnClickListener(v->{UpdateRepository.State state=repository.state().getValue();if(state!=null&&state.release!=null)CardSheet.show(this,getString(R.string.ui134_candidate),((TextView)findViewById(R.id.update_notes)).getText().toString());});
        findViewById(R.id.update_check).setOnClickListener(v -> repository.check());
        findViewById(R.id.update_download).setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 104);
            else repository.download();
        });
        findViewById(R.id.update_cancel).setOnClickListener(v -> repository.cancel());
        findViewById(R.id.update_install).setOnClickListener(v -> install());
        findViewById(R.id.update_browser).setOnClickListener(v -> {
            UpdateRepository.State state = repository.state().getValue();
            AboutDialog.openBrowser(this, state != null && state.release != null ? state.release.pageUrl
                    : "https://github.com/DSH-APP/DSHA/releases");
        });
        repository.state().observe(this, this::renderState);
        repository.installation().observe(this, state -> {
            renderState(repository.state().getValue());
            dispatchInstall();
        });
        if (saved == null && !repository.hasTask()) repository.check();
    }
    private void showRuntimePlan(){
        CardPage page=new CardPage(this,getString(R.string.ui2_managed_update),com.deepseekharness.app.util.UiText.choose("查看当前环境与包内运行组件。","Review the current environment and bundled components."));
        android.widget.LinearLayout metadata=page.card();page.kv(metadata,"Ubuntu",bundledBase());
        page.kv(metadata,"DSH",com.deepseekharness.app.util.Constants.DSH_VERSION);page.kv(metadata,com.deepseekharness.app.util.UiText.choose("个人数据","Personal data"),com.deepseekharness.app.util.UiText.choose("保持原位","Kept in place"));
        metadata.addView(page.text(com.deepseekharness.app.util.UiText.choose("检查后展示实际差异；确认更新时停止 Web 与终端，准备候选并完成隔离试运行。","Review actual differences first. Updating stops Web and terminals, prepares a candidate and verifies it in an isolated trial."),12,R.color.text_secondary));
        var dialog=CardSheet.create(this,page);page.button(page.footer,com.deepseekharness.app.util.UiText.choose("检查更新计划","Review update plan"),true,()->{dialog.dismiss();startActivity(new Intent(this,ExtractActivity.class).putExtra("review_only",true));});page.button(page.footer,com.deepseekharness.app.util.UiText.choose("取消","Cancel"),false,dialog::dismiss);CardSheet.show(dialog,this);
    }
    private String bundledBase(){try(java.io.InputStream input=getAssets().open("offline-rootfs.version")){byte[] bytes=new byte[64];int count=input.read(bytes);return count>0?new String(bytes,0,count,java.nio.charset.StandardCharsets.UTF_8).trim():"—";}catch(java.io.IOException unavailable){return "—";}}
    private void renderState(UpdateRepository.State state) {
        if (state == null) return;
        findViewById(R.id.update_channel_choice).setEnabled(!state.busy&&!repository.installationPending());
        UpdateRepository.InstallState install = repository.installation().getValue();
        UpdateUi.render(findViewById(android.R.id.content), state, install.pending(), install.verifying);
        if (install.pending()) {
            ((TextView) findViewById(R.id.update_status)).setText(install.verifying
                    ? com.deepseekharness.app.util.UiText.text("正在重新校验安装包…") : com.deepseekharness.app.util.UiText.text("校验完成，返回此页面后继续安装"));
        } else if (install.error != null) {
            ((TextView) findViewById(R.id.update_status)).setText(com.deepseekharness.app.util.UiStateText.render(state.message) + "\n" + com.deepseekharness.app.util.UiStateText.render(install.error));
        }
    }

    private void install() {
        if (repository.installationPending()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                resumeInstall = true;
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()))); return;
            }
            repository.requestInstall();
        } catch (Exception error) { resumeInstall = false; showInstallError(error); }
    }
    private void dispatchInstall() {
        if (!installDispatchReady || isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
        java.io.File apk = repository.takeInstallReady();
        if (apk == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception error) { showInstallError(error); }
    }
    private void showInstallError(Exception error) {
        repository.installFailed(com.deepseekharness.app.util.UiText.text("无法安装：") + error.getMessage() + com.deepseekharness.app.util.UiText.text("；可重试"));
        Toast.makeText(this, repository.installation().getValue().error, Toast.LENGTH_LONG).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 104) repository.download();
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putBoolean("resumeInstall", resumeInstall);
        saved.putBoolean("installPending", repository.installationPending());
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
