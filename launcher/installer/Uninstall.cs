// DNZ Launcher uninstaller (in the install folder, listed in Windows' "Installed apps").
using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Windows.Forms;
using Microsoft.VisualBasic.FileIO;
using Microsoft.Win32;

namespace Dnz
{
    class UninstallForm : DnzForm
    {
        enum Step { Ready, Done, Failed }

        Step step = Step.Ready;
        bool removeData;
        string error = "";
        readonly string installDir = Path.GetDirectoryName(Application.ExecutablePath);
        readonly string dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".dnzlauncher");

        public UninstallForm() : base(620, 400, "Uninstall DNZ Launcher")
        {
            ShowReady();
        }

        void ShowReady()
        {
            step = Step.Ready;
            Btn data = null;
            data = new Btn(Btn.Kind.Toggle, new RectangleF(40, 250, 540, 26),
                L.S("Oyun dosyalarını, profilleri ve ayarları da sil", "Also delete game files, profiles and settings"),
                delegate { removeData = !removeData; data.Checked = removeData; Invalidate(); });
            data.Checked = removeData;
            SetButtons(
                data,
                new Btn(Btn.Kind.Secondary, new RectangleF(328, 318, 110, 48), L.S("Vazgeç", "Cancel"), Close),
                new Btn(Btn.Kind.Danger, new RectangleF(450, 318, 130, 48), L.S("KALDIR", "UNINSTALL"), Remove));
        }

        void Remove()
        {
            if (LauncherRunning())
            {
                Fail(L.S("DNZ Launcher şu an açık. Sağ alttaki DNZ simgesine sağ tıklayıp Çıkış de, sonra tekrar dene.",
                    "DNZ Launcher is open. Right-click the DNZ icon at the bottom right, choose Quit, then try again."));
                return;
            }
            try
            {
                foreach (string link in new string[] { DesktopLink(), StartMenuLink(), StartupLink(), UploaderDesktopLink(), UploaderStartMenuLink() })
                {
                    if (File.Exists(link)) File.Delete(link);
                }
                Registry.CurrentUser.DeleteSubKeyTree(UninstallKey, false);
                // Game data goes to the Recycle Bin, so it can still be brought back.
                if (removeData && Directory.Exists(dataDir))
                {
                    FileSystem.DeleteDirectory(dataDir, UIOption.OnlyErrorDialogs, RecycleOption.SendToRecycleBin);
                }
                foreach (string dir in new string[] { "lib", "runtime" })
                {
                    string path = Path.Combine(installDir, dir);
                    if (Directory.Exists(path)) Directory.Delete(path, true);
                }
                string self = Path.GetFullPath(Application.ExecutablePath);
                foreach (string file in Directory.GetFiles(installDir))
                {
                    if (!string.Equals(Path.GetFullPath(file), self, StringComparison.OrdinalIgnoreCase)) File.Delete(file);
                }
                step = Step.Done;
                SetButtons(new Btn(Btn.Kind.Primary, new RectangleF(450, 318, 130, 48), L.S("KAPAT", "CLOSE"), Close));
            }
            catch (Exception ex)
            {
                Fail(L.S("Kaldırılamadı: ", "Could not uninstall: ") + ex.Message);
            }
        }

        void Fail(string message)
        {
            step = Step.Failed;
            error = message;
            SetButtons(
                new Btn(Btn.Kind.Secondary, new RectangleF(328, 318, 110, 48), L.S("Kapat", "Close"), Close),
                new Btn(Btn.Kind.Primary, new RectangleF(450, 318, 130, 48), L.S("TEKRAR DENE", "TRY AGAIN"), ShowReady));
        }

        protected override void Relabel()
        {
            switch (step)
            {
                case Step.Ready: ShowReady(); break;
                case Step.Failed: Fail(error); break;
                default: SetButtons(new Btn(Btn.Kind.Primary, new RectangleF(450, 318, 130, 48), L.S("KAPAT", "CLOSE"), Close)); break;
            }
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            base.OnFormClosed(e);
            if (step != Step.Done) return;
            // This program is still running from the folder: a short hidden command removes the rest after it exits.
            ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", "/c ping 127.0.0.1 -n 3 > nul & rmdir /s /q \"" + installDir + "\"");
            psi.CreateNoWindow = true;
            psi.UseShellExecute = false;
            psi.WindowStyle = ProcessWindowStyle.Hidden;
            try { Process.Start(psi); } catch { }
        }

        protected override void PaintContent(Graphics g)
        {
            Theme.Hero(g, new RectangleF(0, 0, W, 200));
            Theme.Draw(g, "Uninstall DNZ Launcher", Theme.F(12, 1), Color.FromArgb(170, 255, 255, 255), new RectangleF(18, 0, 300, 36), 0);
            RectangleF logo = new RectangleF(40, 70, 72, 72);
            Theme.DrawLogo(g, logo, 16);
            switch (step)
            {
                case Step.Ready:
                    Theme.Draw(g, L.S("DNZ Launcher'ı kaldır", "Uninstall DNZ Launcher"), Theme.F(28, 2), Color.White, new RectangleF(130, 72, 460, 40), 0);
                    Theme.Draw(g, L.S("Program ve kısayolları silinir.", "The program and its shortcuts are removed."), Theme.F(14, 0),
                        Color.FromArgb(215, 255, 255, 255), new RectangleF(132, 110, 460, 26), 0);
                    Theme.Paragraph(g, L.S("İşaretlemezsen profillerin, dünyaların ve ayarların bilgisayarında kalır; tekrar kurunca aynen geri gelir. İşaretlersen hepsi Geri Dönüşüm Kutusu'na gider.",
                        "If you leave this unticked, your profiles, worlds and settings stay on your computer and come back when you install again. If ticked, they go to the Recycle Bin."),
                        Theme.F(12, 0), Theme.Muted, new RectangleF(40, 204, 540, 44));
                    break;
                case Step.Done:
                    Theme.Draw(g, L.S("Kaldırıldı", "Uninstalled"), Theme.F(28, 2), Color.White, new RectangleF(130, 72, 460, 40), 0);
                    Theme.Draw(g, L.S("DNZ Launcher bilgisayarından kaldırıldı.", "DNZ Launcher was removed from your computer."), Theme.F(14, 0),
                        Color.FromArgb(215, 255, 255, 255), new RectangleF(132, 110, 460, 26), 0);
                    break;
                case Step.Failed:
                    Theme.Draw(g, L.S("DNZ Launcher'ı kaldır", "Uninstall DNZ Launcher"), Theme.F(28, 2), Color.White, new RectangleF(130, 72, 460, 40), 0);
                    Theme.Paragraph(g, error, Theme.F(14, 1), Theme.Danger, new RectangleF(40, 212, 540, 90));
                    break;
            }
        }

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new UninstallForm());
        }
    }
}
