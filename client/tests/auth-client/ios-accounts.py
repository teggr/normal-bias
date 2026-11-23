#!/usr/bin/env python3
"""New account screens in the isolated simulator probe. Never targets a physical device."""
import json, subprocess, time
from pathlib import Path
from ios import probe, until, root, out, fixture
checks=[]
def admin(op,*args):
    run=subprocess.run(['java','-jar',str(root/'api/target/normal-bias-api-0.1.0-SNAPSHOT.jar'),'admin',op,*args,'--data-dir='+str(out/'data')],capture_output=True,text=True,check=True)
    return json.loads(run.stdout)
def tap(name): probe('tap',name=name); time.sleep(.3)
def fill(name,value): probe('text',name=name,value=value)
def seen(label): return until(lambda s:any(label in x for x in s['labels']),label)
username='native'+str(int(time.time()));password='Synthetic native account 2468';changed='Synthetic native changed 8642'
seen('Sign in')
tap('Use invitation or reset link');fill('Account link','https://other.test/#invite='+'a'*64);tap('Open link');seen('from this service');checks.append('native pasted foreign-origin link rejected')
invite=admin('invite','--app-origin='+fixture['origin']);fill('Account link',invite['shareLink']);tap('Open link');seen('Choose a password of 12')
fill('Username',username);fill('New password',password);fill('Confirm password','mismatch');tap('Create account');seen('matching confirmation')
fill('Confirm password',password);tap('Create account');seen('Account created.');tap('Continue to sign in')
fill('Username',username);fill('Password',password);tap('Sign in');until(lambda s:s['library'],'new listener library');checks.append('native invitation form validates confirmation and creates usable listener')
tap('Change password');tap('Cancel');until(lambda s:s['library'],'cancel returns library');checks.append('native password cancellation retains library')
tap('Change password');fill('Current password',password);fill('New password',changed);fill('Confirm password',changed);tap('Change password');seen('Sign in');checks.append('native password change requires new sign-in')
reset=admin('reset',username,'--app-origin='+fixture['origin']);tap('Use invitation or reset link');fill('Account link',reset['shareLink']);tap('Open link');seen('Choose a password of 12')
fill('New password',password);fill('Confirm password',password);tap('Reset password');seen('Password reset.');tap('Continue to sign in');fill('Username',username);fill('Password',password);tap('Sign in');until(lambda s:s['library'],'reset login');tap('Sign out');seen('Sign in');checks.append('native reset completion and fresh login/logout')
(out/'ios-accounts-result.json').write_text(json.dumps({'checks':checks},indent=2));print(json.dumps({'checks':checks},indent=2))
