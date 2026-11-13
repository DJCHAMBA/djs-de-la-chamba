package com.djchamba.panel;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpException;
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest;
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YtMusicActivity extends Activity {
    private static final int BG=0xFF090817, CARD=0xFF17132B, FIELD=0xFF0D0B1B, LINE=0xFF40305F, WHITE=0xFFF7F2FF, MUTED=0xFFB8AACD, BLUE=0xFF873BFF, PINK=0xFFFF4FD8, GREEN=0xFF4BD49A, RED=0xFFFF6E9B;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root, results;
    private EditText query;
    private TextView status;
    private boolean ready=false, busy=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        build();
        setStatus("Preparando motor de descargas…", 0xFFFFC45C);
        worker.execute(() -> {
            try {
                String[] abis=Build.SUPPORTED_ABIS;
                boolean supported=false;
                for(String abi:abis) if("arm64-v8a".equals(abi)||"x86_64".equals(abi)) supported=true;
                if(!supported) throw new Exception("El descargador requiere Android de 64 bits (ARM64 o x86_64). El resto del panel sigue disponible.");
                YtDlp.init(getApplicationContext());
                ready=true;
                runOnUiThread(()->setStatus("Motor listo · descargas en este teléfono",GREEN));
            } catch(Exception e) { runOnUiThread(()->setStatus(e.getMessage(),RED)); }
        });
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root=col();root.setPadding(dp(16),dp(18),dp(16),dp(28));root.setBackgroundColor(BG);scroll.addView(root);setContentView(scroll);
        TextView back=text("←  VOLVER A PANEL LA CHAMBA",13,WHITE,true);back.setPadding(dp(12),dp(14),dp(12),dp(14));back.setBackground(shape(CARD,LINE,12));back.setOnClickListener(v->finish());root.addView(back);
        TextView title=text("⬇️  YT DESCARGAR",24,WHITE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.setMargins(0,dp(22),0,dp(5));root.addView(title,tp);
        root.addView(text("Busca música o pega un enlace compatible. Descarga directamente al teléfono.",12,MUTED,false));
        status=text("Preparando motor…",11,0xFFFFC45C,true);status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setBackground(shape(CARD,LINE,12));LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.setMargins(0,dp(14),0,dp(12));root.addView(status,st);
        LinearLayout search=col();search.setPadding(dp(13),dp(13),dp(13),dp(13));search.setBackground(shape(CARD,LINE,15));root.addView(search);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(WHITE);query.setHintTextColor(MUTED);query.setHint("Canción, artista o enlace de YouTube");query.setTextSize(14);query.setPadding(dp(12),dp(11),dp(12),dp(11));query.setBackground(shape(FIELD,LINE,10));search.addView(query,new LinearLayout.LayoutParams(-1,dp(50)));
        Button find=button("🔎  Buscar",true,()->search());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.setMargins(0,dp(9),0,0);search.addView(find,bp);
        TextView dest=text("GUARDADO: Descargas/Panel La Chamba/YT Música",10,PINK,true);LinearLayout.LayoutParams dp0=new LinearLayout.LayoutParams(-1,-2);dp0.setMargins(dp(3),dp(15),0,dp(9));root.addView(dest,dp0);
        results=col();root.addView(results);
        LinearLayout tools=col();tools.setPadding(dp(13),dp(13),dp(13),dp(13));tools.setBackground(shape(CARD,LINE,15));LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.setMargins(0,dp(12),0,0);root.addView(tools,tl);
        tools.addView(text("HERRAMIENTAS",11,PINK,true));
        Button update=button("Actualizar motor yt-dlp",false,()->updateEngine());LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,dp(44));up.setMargins(0,dp(8),0,0);tools.addView(update,up);
        Button folder=button("Abrir carpeta de música",false,()->openDownloads());LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(44));fp.setMargins(0,dp(7),0,0);tools.addView(folder,fp);
        root.addView(text("Usa las descargas respetando los derechos y permisos del contenido.",10,MUTED,false));
    }
    private Button button(String label,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(WHITE);b.setBackground(shape(primary?BLUE:0xFF1A2940,primary?BLUE:LINE,11));b.setOnClickListener(v->action.run());return b;}
    private void setStatus(String message,int color){status.setText(message);status.setTextColor(color);}
    private void search(){
        String q=query.getText().toString().trim();if(q.isEmpty()){toast("Escribe una canción o pega un enlace.");return;}if(!ready){toast("El motor todavía no está listo o este teléfono no es compatible.");return;}if(busy)return;
        busy=true;results.removeAllViews();setStatus("Buscando…",0xFFFFC45C);hideKeyboard();
        worker.execute(()->{
            try{
                boolean url=q.matches("(?i)^https?://.+");
                YtDlpRequest req=new YtDlpRequest(url?q:"ytsearch8:"+q).addOption("--dump-single-json").addOption("--flat-playlist").addOption("--skip-download").addOption("--no-warnings");
                YtDlpResponse response=YtDlp.execute(req,null);
                if(!response.isSuccess())throw new Exception(response.getErrorOutput());
                JSONObject data=new JSONObject(response.getOutput().trim());
                JSONArray entries=data.optJSONArray("entries");
                JSONArray list=entries==null?new JSONArray().put(data):entries;
                runOnUiThread(()->{
                    busy=false;setStatus(list.length()+" resultado(s) · selecciona para descargar",GREEN);
                    for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null)addResult(item);}
                    if(list.length()==0) addResultError("No se encontraron resultados.");
                });
            }catch(Exception e){String msg=shortError(e);runOnUiThread(()->{busy=false;setStatus("No se pudo buscar",RED);addResultError(msg);});}
        });
    }
    private void addResult(JSONObject item){
        String title=item.optString("title","Audio sin título");String uploader=item.optString("uploader",item.optString("channel","YouTube"));String id=item.optString("id","");String url=item.optString("webpage_url","");
        if(url.isEmpty()&&!id.isEmpty())url="https://www.youtube.com/watch?v="+id;
        if(url.isEmpty())return;
        String duration=duration(item.optLong("duration",0));
        LinearLayout card=col();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(shape(CARD,LINE,13));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(6),0,dp(6));results.addView(card,cp);
        card.addView(text(title,14,WHITE,true));TextView meta=text(uploader+" · "+duration,11,MUTED,false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.setMargins(0,dp(5),0,dp(8));card.addView(meta,mp);
        Button dl=button("⬇️  Descargar MP3",true,()->download(url,title));card.addView(dl,new LinearLayout.LayoutParams(-1,dp(44)));
    }
    private void addResultError(String message){TextView t=text(message,12,RED,false);t.setPadding(dp(12),dp(12),dp(12),dp(12));results.addView(t);}
    private String duration(long seconds){if(seconds<=0)return "Duración no disponible";return String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);}
    private void download(String url,String title){
        if(!ready||busy)return;busy=true;setStatus("Preparando descarga…",0xFFFFC45C);
        worker.execute(()->{
            File temp=null,mp3=null;
            try{
                File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"yt-temp");if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear carpeta temporal.");
                File[] before=dir.listFiles();long stamp=System.currentTimeMillis();
                YtDlpRequest req=new YtDlpRequest(url).setOutputTemplate(new File(dir,"%(title).160B [%(id)s].%(ext)s").getAbsolutePath()).addOption("-f","bestaudio[ext=m4a]/bestaudio").addOption("--no-playlist").addOption("--no-warnings").addOption("--no-part");
                YtDlpResponse response=YtDlp.execute(req,(progress,eta,line)->runOnUiThread(()->setStatus(String.format(Locale.US,"Descargando %.0f%% · ETA %ds",progress,eta),0xFFFFC45C)));
                if(!response.isSuccess())throw new Exception(response.getErrorOutput());
                File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile()&&f.lastModified()>=stamp-2000&&(temp==null||f.lastModified()>temp.lastModified()))temp=f;
                if(temp==null)throw new IOException("yt-dlp terminó pero no se encontró el audio descargado.");
                mp3=new File(dir,temp.getName().replaceAll("\\.[^.]+$","")+".mp3");
                String command="-y -i "+quote(temp.getAbsolutePath())+" -vn -codec:a libmp3lame -q:a 2 "+quote(mp3.getAbsolutePath());
                final File source=temp,converted=mp3;
                com.arthenica.ffmpegkit.FFmpegSession session=FFmpegKit.execute(command);
                if(!ReturnCode.isSuccess(session.getReturnCode()))throw new Exception("No se pudo convertir a MP3: "+session.getFailStackTrace());
                Uri saved=saveToDownloads(converted,title);
                if(saved==null)throw new IOException("No se pudo guardar el MP3 en Descargas.");
                source.delete();converted.delete();
                runOnUiThread(()->{busy=false;setStatus("✓ Descarga terminada · guardada en YT Música",GREEN);toast("MP3 guardado en Descargas/Panel La Chamba/YT Música");});
            }catch(Exception e){String msg=shortError(e);runOnUiThread(()->{busy=false;setStatus("Error en la descarga",RED);toast(msg);});}
        });
    }
    private Uri saveToDownloads(File file,String title)throws Exception{
        String safe=(title==null?"Audio":title).replaceAll("[\\\\/:*?\"<>|]","_").trim();if(safe.length()>120)safe=safe.substring(0,120);if(safe.isEmpty())safe="Audio";
        if(Build.VERSION.SDK_INT>=29){
            ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,safe+".mp3");values.put(MediaStore.Downloads.MIME_TYPE,"audio/mpeg");values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Panel La Chamba/YT Música");values.put(MediaStore.Downloads.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Android no pudo crear el archivo de destino.");
            try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("No se pudo abrir destino.");copy(in,out);}
            catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}
            ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(uri,ready,null,null);return uri;
        }
        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"Panel La Chamba/YT Música");if(!dir.exists()&&!dir.mkdirs())throw new IOException("No se pudo crear la carpeta de música.");
        File out=new File(dir,safe+".mp3");try(InputStream in=new FileInputStream(file);OutputStream stream=new FileOutputStream(out)){copy(in,stream);}return Uri.fromFile(out);
    }
    private void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}
    private String quote(String p){return "'"+p.replace("'","'\\''")+"'";}
    private void updateEngine(){
        if(busy)return;setStatus("Actualizando yt-dlp…",0xFFFFC45C);
        YtDlp.updateYtDlp(getApplicationContext(),new YtDlp.UpdateCallback(){public void onComplete(String statusText){runOnUiThread(()->setStatus("Motor actualizado · "+statusText,GREEN));}public void onError(String error){runOnUiThread(()->setStatus("No se pudo actualizar: "+error,RED));}});
    }
    private void openDownloads(){
        try{android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_VIEW);intent.setDataAndType(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,"resource/folder");intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}catch(Exception e){toast("Busca la carpeta Descargas/Panel La Chamba/YT Música en Archivos.");}
    }
    private String shortError(Exception e){String s=e.getMessage();if(s==null||s.trim().isEmpty())s=e.toString();s=s.replaceAll("\\s+"," ").trim();return s.length()>220?s.substring(0,220)+"…":s;}
    private void hideKeyboard(){try{((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);}catch(Exception ignored){}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
