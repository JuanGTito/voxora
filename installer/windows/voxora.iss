; LFTY JGTM SCGC YARS DMFY
#ifndef AppImage
  #error AppImage is required
#endif
#ifndef OutputPath
  #error OutputPath is required
#endif
[Setup]
AppId={{AA52C709-3EE0-49AE-BE41-2A794B0C521A}
AppName=Voxora
AppVersion=0.2.0
AppPublisher=Equipo Voxora
AppPublisherURL=https://github.com/JuanGTito/voxora
DefaultDirName={localappdata}\Programs\Voxora
DefaultGroupName=Voxora
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=no
WizardStyle=modern
SetupIconFile=..\..\src\main\resources\icons\voxora.ico
UninstallDisplayIcon={app}\Voxora.exe
OutputDir={#OutputPath}
OutputBaseFilename=Voxora-Setup-0.2.0-windows-x64
Compression=lzma2/fast
SolidCompression=no
InfoBeforeFile=bienvenida.txt
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: unchecked

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "*.gguf"
Source: "{#AppImage}\app\ai\models\*.gguf"; DestDir: "{app}\app\ai\models"; Flags: ignoreversion nocompression
Source: "..\..\docs\THIRD_PARTY.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Voxora"; Filename: "{app}\Voxora.exe"; WorkingDir: "{app}"
Name: "{group}\Desinstalar Voxora"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Voxora"; Filename: "{app}\Voxora.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\Voxora.exe"; Description: "Abrir Voxora"; Flags: nowait postinstall skipifsilent
