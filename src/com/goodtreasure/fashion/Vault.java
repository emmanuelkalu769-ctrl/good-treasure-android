package com.goodtreasure.fashion;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Tokens, measurements and addresses are encrypted with a device-bound key. */
public final class Vault {
 private final SharedPreferences preferences;
 private static final String ALIAS="goodtreasure.native.v2";
 public Vault(Context context){preferences=context.getSharedPreferences("native-vault",Context.MODE_PRIVATE);}
 private SecretKey key() throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generator.generateKey();}
  return (SecretKey)store.getKey(ALIAS,null);
 }
 public synchronized JSONObject read(String name){
  try{String raw=preferences.getString(name,null);if(raw==null)return new JSONObject();JSONObject envelope=new JSONObject(raw);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(envelope.getString("iv"),Base64.NO_WRAP)));cipher.updateAAD(name.getBytes("UTF-8"));return new JSONObject(new String(cipher.doFinal(Base64.decode(envelope.getString("data"),Base64.NO_WRAP)),"UTF-8"));}
  catch(Exception failure){preferences.edit().remove(name).apply();return new JSONObject();}
 }
 public synchronized void write(String name,JSONObject value){
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(name.getBytes("UTF-8"));JSONObject envelope=new JSONObject();envelope.put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));envelope.put("data",Base64.encodeToString(cipher.doFinal(value.toString().getBytes("UTF-8")),Base64.NO_WRAP));preferences.edit().putString(name,envelope.toString()).apply();}
  catch(Exception failure){throw new IllegalStateException("Could not securely save your details.");}
 }
 public synchronized void remove(String name){preferences.edit().remove(name).apply();}
}
