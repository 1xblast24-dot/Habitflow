import os, shutil
os.makedirs('www', exist_ok=True)
for f in ['app.html', 'login.html', 'offline.html', 'manifest.webmanifest']:
    shutil.copy('../' + f, 'www/' + f)
shutil.copytree('../icons', 'www/icons', dirs_exist_ok=True)
for f in ['www/app.html', 'www/login.html']:
    s = open(f, encoding='utf-8').read()
    for q in ("'", '"'):
        s = s.replace(q + '/login' + q, q + 'login.html' + q).replace(q + '/app' + q, q + 'app.html' + q)
    s = s.replace('<script src="pwa.js" defer></script>', '')
    open(f, 'w', encoding='utf-8').write(s)
open('www/index.html', 'w').write('<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=login.html"></head><body></body></html>')
print('web app bundled into www/')
