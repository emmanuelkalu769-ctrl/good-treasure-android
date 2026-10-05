package com.goodtreasure.fashion;
import android.app.Activity;
import android.os.CancellationSignal;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.*;
import java.util.concurrent.Executor;

/** Google account selection uses the native Credential Manager, never a browser. */
public final class NativeGoogle {
 public interface Callback {void token(String token);void error(String message);}
 public static CancellationSignal signIn(Activity activity,String client,String nonce,Executor executor,Callback callback){
  CancellationSignal cancel=new CancellationSignal();
  GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(client).setNonce(nonce).build();
  GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
  CredentialManager.create(activity).getCredentialAsync(activity,request,cancel,executor,new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
   public void onResult(GetCredentialResponse response){
    try {Credential c=response.getCredential();if(!(c instanceof CustomCredential)||!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())){callback.error("Google did not return an account. Please try again.");return;}callback.token(GoogleIdTokenCredential.createFrom(c.getData()).getIdToken());}
    catch(Exception failure){callback.error("Could not read Google's sign-in response. Please try again.");}
   }
   public void onError(GetCredentialException failure){
    if(failure instanceof GetCredentialCancellationException)callback.error("Sign-in cancelled.");
    else if(failure instanceof NoCredentialException)callback.error("Add a Google account in your phone's Settings, then try again.");
    else callback.error("Google sign-in could not start. Check the Android OAuth client and this APK's signing fingerprint, and update Google Play services.");
   }
  });return cancel;
 }
 public static void clear(Activity activity,Executor executor){CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(),null,executor,new CredentialManagerCallback<Void,ClearCredentialException>(){public void onResult(Void value){}public void onError(ClearCredentialException failure){}});}
}
