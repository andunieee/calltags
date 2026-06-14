# Models persisted with Gson (e.g. the remembered SIM per number) must keep their field names,
# otherwise R8 could rename them between releases and stored values would stop loading.
-keepclassmembers class com.calltags.app.models.** {
    <fields>;
}
