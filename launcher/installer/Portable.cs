using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace Dnz
{
    /// <summary>
    /// "DNZ Launcher.exe" of the portable zip: starts the launcher with the Java next to it. Nothing is installed;
    /// the folder can be anywhere (desktop, USB stick).
    /// </summary>
    static class Portable
    {
        [STAThread]
        static void Main()
        {
            string dir = AppDomain.CurrentDomain.BaseDirectory;
            string java = Path.Combine(dir, "runtime\\bin\\javaw.exe");
            if (!File.Exists(java) || !Directory.Exists(Path.Combine(dir, "lib")))
            {
                MessageBox.Show(
                    "Some files of DNZ Launcher are missing. Unzip the whole folder first, then start DNZ Launcher.exe from it.\n\n" +
                    "DNZ Launcher dosyalarından bazıları eksik. Önce klasörün tamamını zipten çıkar, sonra içindeki DNZ Launcher.exe'yi aç.",
                    "DNZ Launcher", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            ProcessStartInfo start = new ProcessStartInfo(java, "-XX:+UseSerialGC -Xss2m -cp \"lib\\*\" dnz.launcher.MainKt");
            start.WorkingDirectory = dir;
            start.UseShellExecute = false;
            Process.Start(start);
        }
    }
}
