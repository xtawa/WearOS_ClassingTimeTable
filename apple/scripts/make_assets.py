from pathlib import Path
from PIL import Image
import json
ROOT=Path(__file__).resolve().parents[1]
source=ROOT.parent/'mobile/src/main/res/drawable-nodpi/classing_icon_light.png'
image=Image.open(source).convert('RGB')
assets=ROOT/'Assets.xcassets';icons=assets/'AppIcon.appiconset';icons.mkdir(parents=True,exist_ok=True)
(assets/'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}}),encoding='utf-8')
image.resize((1024,1024),Image.Resampling.LANCZOS).save(icons/'AppIcon.png')
entries=[{'filename':'AppIcon.png','idiom':'universal','platform':'ios','size':'1024x1024'}]
for size,scale,role,subtype in [('24x24','2x','notificationCenter','38mm'),('27.5x27.5','2x','notificationCenter','42mm'),('29x29','2x','companionSettings',None),('29x29','3x','companionSettings',None),('40x40','2x','appLauncher','38mm'),('44x44','2x','appLauncher','40mm'),('46x46','2x','appLauncher','41mm'),('50x50','2x','appLauncher','44mm'),('51x51','2x','appLauncher','45mm'),('54x54','2x','appLauncher','49mm'),('86x86','2x','quickLook','38mm'),('98x98','2x','quickLook','42mm'),('108x108','2x','quickLook','44mm')]:
    pixels=round(float(size.split('x')[0])*int(scale[0]));filename=f'watch-{pixels}.png';image.resize((pixels,pixels),Image.Resampling.LANCZOS).save(icons/filename)
    row={'filename':filename,'idiom':'watch','size':size,'scale':scale,'role':role}
    if subtype:row['subtype']=subtype
    entries.append(row)
entries.append({'filename':'AppIcon.png','idiom':'watch-marketing','size':'1024x1024','scale':'1x'})
(icons/'Contents.json').write_text(json.dumps({'images':entries,'info':{'author':'xcode','version':1}},indent=2),encoding='utf-8')
accent=assets/'AccentColor.colorset';accent.mkdir(exist_ok=True)
(accent/'Contents.json').write_text(json.dumps({'colors':[{'idiom':'universal','color':{'color-space':'srgb','components':{'red':'0.314','green':'0.349','blue':'0.812','alpha':'1.000'}}}],'info':{'author':'xcode','version':1}},indent=2),encoding='utf-8')
preview=ROOT/'Preview/assets';preview.mkdir(exist_ok=True);image.resize((96,96),Image.Resampling.LANCZOS).save(preview/'icon.png')
