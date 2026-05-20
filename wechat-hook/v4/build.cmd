@echo off
dotnet publish WeChatHook.csproj -c Release -f net8.0-windows -r win-x64 --sc false -o ./Out -p:Platform=x64 -p:PublishSingleFile=true
del .\Out\*.pdb
move .\Out\WeChatHook.exe .\Out\WSXPay.Hook.WeChat.exe