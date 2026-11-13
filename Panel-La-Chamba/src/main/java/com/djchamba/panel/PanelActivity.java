package com.djchamba.panel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.*;
import org.json.JSONObject;

public class PanelActivity extends Activity {
    private static final int BG=0xFF090817, CARD=0xFF17132B, FIELD=0xFF0D0B1B, LINE=0xFF40305F, WHITE=0xFFF7F2FF, MUTED=0xFFB8AACD, BLUE=0xFF873BFF, GREEN=0xFF4BD49A, RED=0xFFFF6E9B;
    private static final String DEFAULT_URL="http://187.33.156.186:3000/comando";
    private static final int PINK=0xFFFF4FD8;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root, logList;
    private TextView status, multiCount;
    private EditText endpoint, musicId, musicPitch, djName, message, announcement, multiIds, volume, pitch, djNow;
    private int pending=0;

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);prefs=getSharedPreferences("panel",MODE_PRIVATE);build();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root=column();root.setPadding(dp(18),dp(22),dp(18),dp(30));root.setBackgroundColor(BG);scroll.addView(root);setContentView(scroll);
        LinearLayout header=row();android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(R.drawable.panel_launcher_foreground);logo.setBackground(shape(0xFF241044,0xFF8D42FF,16));logo.setPadding(dp(5),dp(5),dp(5),dp(5));header.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout titles=column();titles.setPadding(dp(12),0,0,0);titles.addView(text("PANEL LA CHAMBA",20,WHITE,true));titles.addView(text("MOBILE  ·  DJ CONTROL SYSTEM",10,PINK,true));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        TextView hero=text("Tu cabina de comandos.\nLa Chamba en tu bolsillo.",25,WHITE,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(22),0,dp(6));root.addView(hero,hp);root.addView(text("Controla tu servidor de Roblox desde el teléfono.",13,MUTED,false));
        LinearLayout statusRow=row();status=text("●  Listo para conectar",12,0xFFFFC45C,true);status.setPadding(dp(13),dp(10),dp(13),dp(10));status.setBackground(shape(CARD,LINE,24));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMargins(0,dp(18),0,dp(12));root.addView(statusRow);statusRow.addView(status,sp);
        ytDownloaderCard();musicCard();messageCard();announceCard();multiCard();audioCard();robloxToolsCard();playbackCard();mapCommandsCard();connectionCard();historyCard();
        TextView footer=text("PANEL LA CHAMBA · APLICACIÓN ANDROID NATIVA",9,0xFF687991,true);footer.setGravity(Gravity.CENTER);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.setMargins(0,dp(20),0,0);root.addView(footer,fp);
    }


    private void ytDownloaderCard(){
        LinearLayout c=card("⬇️  YT Descargar","Busca canciones y descarga MP3 directamente en este teléfono, sin usar la PC.");
        addButton(c,"🎧 Abrir YT Descargar",true,()->startActivity(new Intent(this,YtMusicActivity.class)));
        TextView note=text("Las descargas se guardan en Descargas/Panel La Chamba/YT Música.",10,MUTED,false);note.setPadding(0,dp(5),0,0);c.addView(note);
    }
    private void robloxToolsCard(){
        LinearLayout c=card("🛠️  Herramientas Roblox","Abre las herramientas oficiales de La Chamba dentro de esta misma aplicación.");
        addButton(c,"⬆️ Subir audios a Roblox",true,()->openRobloxTools("upload"));
        addButton(c,"🎚️ Editor Roblox rápido",false,()->openRobloxTools("editor"));
        addButton(c,"🔐 Permisos Roblox (Admin)",false,()->openRobloxTools("permissions"));
        TextView note=text("El inicio de sesión de administrador se valida en el servidor. La app no guarda ni incluye claves API de Roblox.",10,MUTED,false);note.setPadding(0,dp(6),0,0);c.addView(note);
    }
    private void openRobloxTools(String section){
        Intent intent=new Intent(this,RobloxToolsActivity.class);
        intent.putExtra("section",section);
        startActivity(intent);
    }

    private LinearLayout card(String title,String subtitle){LinearLayout c=column();c.setPadding(dp(15),dp(15),dp(15),dp(15));c.setBackground(shape(CARD,LINE,17));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));root.addView(c,p);c.addView(text(title,15,WHITE,true));if(subtitle!=null&&!subtitle.isEmpty()){TextView sub=text(subtitle,11,MUTED,false);sub.setPadding(0,dp(5),0,dp(8));c.addView(sub);}return c;}
    private EditText field(LinearLayout c,String label,String hint,boolean multi){c.addView(text(label.toUpperCase(),10,0xFF9BB8E0,true));EditText e=new EditText(this);e.setTextColor(WHITE);e.setHintTextColor(0xFF687991);e.setTextSize(14);e.setSingleLine(!multi);e.setHint(hint);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(shape(FIELD,LINE,11));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,multi?dp(105):dp(48));p.setMargins(0,dp(6),0,dp(11));c.addView(e,p);return e;}
    private Button button(String label,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(WHITE);b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setBackground(shape(primary?BLUE:0xFF1A2940,primary?BLUE:LINE,11));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout c,String label,boolean primary,Runnable action){Button b=button(label,primary,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.setMargins(0,dp(2),0,dp(5));c.addView(b,p);}
    private void musicCard(){LinearLayout c=card("🎵  Enviar música","Envía una canción a la cola o fuerza su reproducción.");musicId=field(c,"ID DE AUDIO ROBLOX","Ej. 131242819094093",false);musicPitch=field(c,"PITCH OPCIONAL","1.0 (opcional)",false);LinearLayout r=row();r.addView(button("🎶 Enviar música",true,()->music(false)),new LinearLayout.LayoutParams(0,dp(46),1));Space gap=new Space(this);r.addView(gap,new LinearLayout.LayoutParams(dp(8),1));r.addView(button("⚡ Reproducir ahora",false,()->music(true)),new LinearLayout.LayoutParams(0,dp(46),1));c.addView(r);}
    private void messageCard(){LinearLayout c=card("💬  Enviar mensaje","Publica un mensaje M2 con el nombre del DJ.");djName=field(c,"NOMBRE DEL DJ","DJS DE LA CHAMBA",false);djName.setText("DJS DE LA CHAMBA");message=field(c,"MENSAJE","Escribe el mensaje...",true);addButton(c,"💬 Enviar mensaje",true,()->{String n=djName.getText().toString().trim(),m=message.getText().toString().trim();if(n.isEmpty()||m.isEmpty()){toast("Completa el DJ y el mensaje.");return;}send("m2 "+n.replace("|"," ").replace("\\n"," ").substring(0,Math.min(50,n.length()))+"|"+m.substring(0,Math.min(350,m.length())));});}
    private void announceCard(){LinearLayout c=card("📢  Anuncio de La Chamba","Envía un anuncio a los jugadores.");announcement=field(c,"TEXTO DEL ANUNCIO","Escribe el anuncio...",true);addButton(c,"📢 Enviar anuncio",true,()->{String m=announcement.getText().toString().trim();if(m.isEmpty()){toast("Escribe el anuncio.");return;}send("chamba "+m.substring(0,Math.min(350,m.length())));});}
    private void multiCard(){LinearLayout c=card("🎶  Enviar varias canciones","Pega tantos IDs como necesites. Sin límite artificial en la app; el servidor puede tener límites propios.");multiIds=field(c,"IDS DE AUDIO · SIN LÍMITE FIJO","Pega IDs separados por comas, espacios o saltos de línea",true);multiCount=text("0 IDs detectados",10,CYAN(),true);c.addView(multiCount);multiIds.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){multiCount.setText(parseIds().length+" IDs detectados");}public void afterTextChanged(android.text.Editable e){}});addButton(c,"🎶 Enviar lista completa",true,()->{String[] ids=parseIds();if(ids.length==0){toast("Pega al menos un ID.");return;}StringBuilder cmd=new StringBuilder("multimusica ");for(int i=0;i<ids.length;i++){if(!ids[i].matches("\\d+")){toast("Hay un ID inválido: "+ids[i]);return;}if(i>0)cmd.append(',');cmd.append(ids[i]);}send(cmd.toString());});}
    private int CYAN(){return 0xFF39C5DF;}
    private String[] parseIds(){String raw=multiIds.getText().toString().trim();return raw.isEmpty()?new String[0]:raw.split("[,;\\s]+");}
    private void audioCard(){LinearLayout c=card("🎚️  Audio y DJ","Ajusta parámetros y anuncia quién está tocando.");volume=field(c,"VOLUMEN (0–10)","5",false);volume.setText("5");pitch=field(c,"PITCH (0.1–3)","1",false);pitch.setText("1");addButton(c,"Aplicar volumen",false,()->numberCommand("volumen",volume,0,10));addButton(c,"Aplicar pitch",false,()->numberCommand("pitch",pitch,.1,3));djNow=field(c,"DJ QUE ESTÁ TOCANDO","Nombre del DJ",false);addButton(c,"🎧 Anunciar DJ",false,()->{String n=djNow.getText().toString().trim();if(n.isEmpty()){toast("Escribe el nombre del DJ.");return;}send("tocando "+n);});}
    private void numberCommand(String cmd,EditText e,double min,double max){try{double n=Double.parseDouble(e.getText().toString().trim());if(n<min||n>max)throw new Exception();send(cmd+" "+e.getText().toString().trim());}catch(Exception ex){toast("Valor fuera del rango permitido.");}}
    private void playbackCard(){LinearLayout c=card("⏯️  Reproducción y modos","Controles rápidos para el sistema de música.");String[][] items={{"⏭ Siguiente","skip"},{"⏸ Pausar","pause"},{"▶ Continuar","resume"},{"⏮ Reiniciar","resetear"},{"🎧 Activar modo DJ","mododj"},{"🚫 Desactivar modo DJ","nomododj"},{"🔁 Activar loop","loop"},{"↩ Quitar loop","unloop"},{"✨ Realista","realista"},{"🌙 No realista","norealista"}};for(String[] it:items)addButton(c,it[0],false,()->send(it[1]));}
    private void mapCommandsCard(){
        LinearLayout c=card("🗺️  Eventos y control del mapa","Comandos nuevos compatibles con el puente Roblox.");
        String[] labels={"🎉 Piñata","🎆 Fuegos artificiales","🇲🇽 Evento México","🔦 Show láser","🫧 Fiesta de espuma","🫧 Lluvia de burbujas","💸 Lluvia de dinero","🌎 Terremoto","🧹 Restaurar mapa","💥 Cuenta regresiva de explosión","🏡 Cargar Hacienda La Chamba","🗺️ Mapa","🌦️ Clima","🎨 Temática","🪩 Diseño del piso"};
        String[] commands={"pinata","fuegos","mexico","lazer","espuma","burbujas","dinero","temblor","resetmapa","explotar","mapa1","mapa","clima","tematica","diseno"};
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);spinner.setAdapter(adapter);c.addView(spinner);
        EditText args=field(c,"ARGUMENTOS (SI APLICA)","Ej. lluvia · navidad · 12 · off",false);
        addButton(c,"🚀 Ejecutar comando",true,()->{
            int index=spinner.getSelectedItemPosition();String name=commands[index],value=args.getText().toString().trim();
            boolean required=name.equals("mapa")||name.equals("clima")||name.equals("tematica")||name.equals("diseno");
            if(required&&value.isEmpty()){toast("Este comando necesita argumentos.");return;}
            if(name.equals("diseno")&&!value.matches("([1-9]|[1-6][0-9]|7[0-5])")){toast("El diseño debe ser un número del 1 al 75.");return;}
            if(name.equals("clima")&&!value.matches("(?i)(normal|dia|noche|nublado|lluvia|tormenta|nieve)")){toast("Clima: normal, dia, noche, nublado, lluvia, tormenta o nieve.");return;}
            send(name+(value.isEmpty()?"":" "+value));
        });
        TextView note=text("Otros comandos del bot, como fly, god, aura, dj o panelmusica, requieren el contexto de Roblox/Consola Admin y no están disponibles por este puente directo.",10,MUTED,false);note.setPadding(0,dp(6),0,0);c.addView(note);
    }
    private void connectionCard(){LinearLayout c=card("⚙️  Conexión del servidor","Dirección del puente HTTP de Roblox en Clouding.");endpoint=field(c,"ENDPOINT","http://187.33.156.186:3000/comando",false);endpoint.setText(prefs.getString("endpoint",DEFAULT_URL));addButton(c,"Guardar dirección",false,()->saveEndpoint());}
    private void saveEndpoint(){String s=endpoint.getText().toString().trim();try{URL u=new URL(s);if(!u.getProtocol().equals("http")&&!u.getProtocol().equals("https"))throw new Exception();prefs.edit().putString("endpoint",s).apply();toast("Dirección guardada.");}catch(Exception e){toast("Dirección HTTP/HTTPS inválida.");}}
    private void historyCard(){LinearLayout c=card("📜  Actividad reciente","Últimos resultados de envío.");logList=column();c.addView(logList);TextView empty=text("Aún no has enviado comandos.",11,MUTED,false);logList.addView(empty);}
    private void music(boolean force){String id=musicId.getText().toString().trim();if(!id.matches("\\d{1,25}")){toast("Escribe un ID de audio numérico.");return;}String p=musicPitch.getText().toString().trim();String cmd=(force?"force ":"musica ")+id;if(!p.isEmpty())try{double n=Double.parseDouble(p);if(n<.1||n>3)throw new Exception();cmd+=" "+p;}catch(Exception e){toast("Pitch debe estar entre 0.1 y 3.");return;}send(cmd);}
    private void send(String command){
        if(pending>0){toast("Espera a que termine el envío anterior.");return;}
        saveEndpoint(); pending++; status.setText("●  Enviando comando…"); status.setTextColor(0xFFFFC45C);
        final String endpointUrl=prefs.getString("endpoint",DEFAULT_URL);
        executor.execute(()->{
            HttpURLConnection conn=null;
            try{
                URL url=new URL(endpointUrl); conn=(HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST"); conn.setConnectTimeout(8000); conn.setReadTimeout(15000);
                conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                byte[] body=new JSONObject().put("comando",command).toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream output=conn.getOutputStream()){output.write(body);}
                int code=conn.getResponseCode();
                InputStream in=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();
                String response=read(in);
                if(code<200||code>=300)throw new IOException("HTTP "+code+": "+response);
                runOnUiThread(()->{pending=0;status.setText("●  Servidor conectado · último envío aceptado");status.setTextColor(GREEN);log(command,true,"HTTP "+code+" · "+response);toast("Comando enviado.");});
            }catch(Exception e){
                String msg=e.getMessage()==null?"Error de conexión":e.getMessage();
                runOnUiThread(()->{pending=0;status.setText("●  Error de envío");status.setTextColor(RED);log(command,false,msg);toast(msg);});
            }finally{if(conn!=null)conn.disconnect();}
        });
    }
    private String read(InputStream in)throws IOException{if(in==null)return "";ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    private String jsonEscape(String s){return s.replace("\\\\","\\\\\\\\").replace("\\\"","\\\\\\\"").replace("\\n","\\\\n").replace("\\r","\\\\r").replace("\\t","\\\\t");}
    private void log(String cmd,boolean ok,String msg){TextView t=text((ok?"✓  ":"✕  ")+cmd+"\\n"+msg,11,ok?GREEN:RED,false);t.setPadding(dp(9),dp(9),dp(9),dp(9));t.setBackground(shape(FIELD,LINE,10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));logList.addView(t,0,p);while(logList.getChildCount()>20)logList.removeViewAt(logList.getChildCount()-1);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
