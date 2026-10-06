// DNZ Launcher setup: installs for the current user (no admin rights), in the launcher's own look.
using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace Dnz
{
    class SetupForm : DnzForm
    {
        enum Step { Ready, Installing, Done, Failed }

        Step step = Step.Ready;
        string installDir = Path.Combine(Programs(), "DNZ Launcher");
        bool desktop = true;
        bool startMenu = true;
        bool openNow = true;
        readonly bool update;
        volatile float progress;
        float shownProgress;
        volatile string status = "";
        string error = "";

        public SetupForm() : base(820, 500, "DNZ Launcher Setup")
        {
            update = File.Exists(JavaPath(installDir));
            ShowReady();
        }

        void ShowReady()
        {
            step = Step.Ready;
            Btn desktopBox = null;
            Btn startBox = null;
            desktopBox = new Btn(Btn.Kind.Toggle, new RectangleF(X, 262, 300, 26), L.S("Masaüstü kısayolu", "Desktop shortcut"),
                delegate { desktop = !desktop; desktopBox.Checked = desktop; Invalidate(); });
            desktopBox.Checked = desktop;
            startBox = new Btn(Btn.Kind.Toggle, new RectangleF(X, 296, 300, 26), L.S("Başlat menüsü kısayolu", "Start menu shortcut"),
                delegate { startMenu = !startMenu; startBox.Checked = startMenu; Invalidate(); });
            startBox.Checked = startMenu;
            SetButtons(
                new Btn(Btn.Kind.Secondary, new RectangleF(X + 350, 196, 110, 44), L.S("Değiştir", "Change"), ChooseFolder),
                desktopBox,
                startBox,
                new Btn(Btn.Kind.Primary, new RectangleF(X, 410, 170, 52), update ? L.S("Güncelle", "Update") : L.S("Kur", "Install"), StartInstall),
                new Btn(Btn.Kind.Link, new RectangleF(X + 290, 421, 170, 30), L.S("Gizlilik politikası", "Privacy policy"), OpenPrivacy));
        }

        /// <summary>Left edge of the content on the right of the decoration.</summary>
        const float X = 330;

        /// <summary>Privacy policy on the website (DNZ collects nothing; see PRIVACY.md).</summary>
        static void OpenPrivacy()
        {
            try { Process.Start(new ProcessStartInfo("https://dnzclient.com/privacy-policy") { UseShellExecute = true }); } catch { }
        }

        void ChooseFolder()
        {
            using (FolderBrowserDialog d = new FolderBrowserDialog())
            {
                d.Description = L.S("DNZ Launcher nereye kurulsun?", "Where should DNZ Launcher be installed?");
                d.SelectedPath = Directory.Exists(installDir) ? installDir : Programs();
                if (d.ShowDialog(this) == DialogResult.OK)
                {
                    string chosen = d.SelectedPath;
                    // A folder of its own, so uninstalling never removes anything else.
                    installDir = Path.GetFileName(chosen.TrimEnd('\\')) == "DNZ Launcher" ? chosen : Path.Combine(chosen, "DNZ Launcher");
                    Invalidate();
                }
            }
        }

        void StartInstall()
        {
            if (LauncherRunning())
            {
                Fail(L.S("DNZ Launcher şu an açık. Sağ alttaki DNZ simgesine sağ tıklayıp Çıkış de, sonra tekrar dene.",
                    "DNZ Launcher is open. Right-click the DNZ icon at the bottom right, choose Quit, then try again."));
                return;
            }
            step = Step.Installing;
            progress = 0;
            shownProgress = 0;
            status = L.S("Hazırlanıyor...", "Preparing...");
            SetButtons();
            Thread t = new Thread(Install);
            t.IsBackground = true;
            t.Start();
        }

        void Install()
        {
            try
            {
                Directory.CreateDirectory(installDir);
                string root = Path.GetFullPath(installDir).TrimEnd('\\') + "\\";
                // An update replaces the program files only; profiles, worlds and settings live in the user folder.
                foreach (string old in new string[] { "lib", "runtime" })
                {
                    string dir = Path.Combine(installDir, old);
                    if (Directory.Exists(dir)) Directory.Delete(dir, true);
                }
                status = L.S("Dosyalar kopyalanıyor...", "Copying files...");
                using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
                using (ZipArchive zip = new ZipArchive(s, ZipArchiveMode.Read))
                {
                    long total = 0;
                    foreach (ZipArchiveEntry e in zip.Entries) total += e.Length;
                    long done = 0;
                    byte[] buffer = new byte[1 << 16];
                    foreach (ZipArchiveEntry e in zip.Entries)
                    {
                        string target = Path.GetFullPath(Path.Combine(installDir, e.FullName.Replace('/', '\\')));
                        if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) continue;
                        if (e.Name.Length == 0)
                        {
                            Directory.CreateDirectory(target);
                            continue;
                        }
                        Directory.CreateDirectory(Path.GetDirectoryName(target));
                        using (Stream input = e.Open())
                        using (FileStream output = File.Create(target))
                        {
                            int n;
                            while ((n = input.Read(buffer, 0, buffer.Length)) > 0)
                            {
                                output.Write(buffer, 0, n);
                                done += n;
                                progress = total > 0 ? 0.95f * done / total : 0.95f;
                            }
                        }
                    }
                }

                status = L.S("Kısayollar oluşturuluyor...", "Creating shortcuts...");
                string icon = Path.Combine(installDir, "dnz.ico");
                Link(DesktopLink(), desktop, JavaPath(installDir), JavaArgs(), icon);
                Link(StartMenuLink(), startMenu, JavaPath(installDir), JavaArgs(), icon);
                // Shortcuts of the old Workshop Uploader (earlier versions made them).
                foreach (string link in new string[] { UploaderDesktopLink(), UploaderStartMenuLink() })
                {
                    try { if (File.Exists(link)) File.Delete(link); } catch { }
                }
                Register(icon);
                progress = 1f;
                Thread.Sleep(350);
                BeginInvoke((MethodInvoker)ShowDone);
            }
            catch (Exception ex)
            {
                string message = ex.Message;
                BeginInvoke((MethodInvoker)delegate { Fail(L.S("Kurulum tamamlanamadı: ", "Setup could not finish: ") + message); });
            }
        }

        /// <summary>
        /// The launcher starts with the (signed) Java inside the install folder, so Windows' app control never blocks it.
        /// Working folder = install folder: the launcher finds its "lib" there (also for "start with Windows").
        /// </summary>
        static string JavaPath(string dir)
        {
            return Path.Combine(dir, "runtime\\bin\\javaw.exe");
        }

        static string JavaArgs()
        {
            return "-XX:+UseSerialGC -Xss2m -cp \"lib\\*\" dnz.launcher.MainKt";
        }

        static void Link(string path, bool wanted, string target, string args, string icon, string description = "DNZ Launcher")
        {
            // Not wanted: nothing is made (and a shortcut the player made themselves is never touched).
            if (!wanted) return;
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            object shell = Activator.CreateInstance(shellType);
            try
            {
                object link = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { path });
                Type t = link.GetType();
                t.InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { target });
                t.InvokeMember("Arguments", BindingFlags.SetProperty, null, link, new object[] { args });
                t.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, link, new object[] { Path.GetFullPath(Path.Combine(Path.GetDirectoryName(target), "..\\..")) });
                t.InvokeMember("IconLocation", BindingFlags.SetProperty, null, link, new object[] { icon });
                t.InvokeMember("Description", BindingFlags.SetProperty, null, link, new object[] { description });
                t.InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
                Marshal.ReleaseComObject(link);
            }
            finally
            {
                Marshal.ReleaseComObject(shell);
            }
        }

        /// <summary>Shows DNZ Launcher in Windows' "Installed apps" with its uninstaller.</summary>
        void Register(string icon)
        {
            long bytes = 0;
            foreach (string f in Directory.GetFiles(installDir, "*", SearchOption.AllDirectories)) bytes += new FileInfo(f).Length;
            using (RegistryKey k = Registry.CurrentUser.CreateSubKey(UninstallKey))
            {
                k.SetValue("DisplayName", "DNZ Launcher");
                k.SetValue("DisplayVersion", BuildInfo.Version);
                k.SetValue("Publisher", "DNZ");
                k.SetValue("DisplayIcon", icon);
                k.SetValue("InstallLocation", installDir);
                k.SetValue("UninstallString", "\"" + Path.Combine(installDir, "Uninstall DNZ Launcher.exe") + "\"");
                k.SetValue("EstimatedSize", (int)(bytes / 1024), RegistryValueKind.DWord);
                k.SetValue("NoModify", 1, RegistryValueKind.DWord);
                k.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                k.SetValue("InstallerLanguage", L.Turkish ? "TR" : "EN");
            }
            // First install: the launcher starts in the language picked here (an existing setting is kept).
            string settings = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".dnzlauncher", "launcher.json");
            if (!File.Exists(settings))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(settings));
                File.WriteAllText(settings, "{ \"language\": \"" + (L.Turkish ? "TR" : "EN") + "\" }");
            }
        }

        void ShowDone()
        {
            step = Step.Done;
            Btn open = null;
            open = new Btn(Btn.Kind.Switch, new RectangleF(X + 16, 178, 428, 40), L.S("Launcher'ı şimdi aç", "Open the launcher now"),
                delegate { openNow = !openNow; open.Checked = openNow; Invalidate(); });
            open.Checked = openNow;
            SetButtons(open, new Btn(Btn.Kind.Primary, new RectangleF(X, 410, 140, 52), L.S("Bitir", "Finish"), delegate
            {
                if (openNow)
                {
                    try
                    {
                        ProcessStartInfo psi = new ProcessStartInfo(JavaPath(installDir), JavaArgs());
                        psi.WorkingDirectory = installDir;
                        psi.UseShellExecute = false;
                        Process.Start(psi);
                    }
                    catch { }
                }
                Close();
            }));
        }

        void Fail(string message)
        {
            step = Step.Failed;
            error = message;
            SetButtons(new Btn(Btn.Kind.Primary, new RectangleF(X, 410, 170, 52), L.S("Tekrar dene", "Try again"), ShowReady));
        }

        protected override bool CanClose()
        {
            return step != Step.Installing;
        }

        protected override void Relabel()
        {
            switch (step)
            {
                case Step.Ready: ShowReady(); break;
                case Step.Done: ShowDone(); break;
                case Step.Failed: Fail(error); break;
                default: SetButtons(); break;
            }
        }

        protected override bool Tick()
        {
            if (step != Step.Installing) return false;
            shownProgress += (progress - shownProgress) * 0.2f;
            return true;
        }

        protected override void PaintContent(Graphics g)
        {
            Decoration(g, step == Step.Done);
            switch (step)
            {
                case Step.Ready:
                    Theme.Draw(g, update ? L.S("DNZ Launcher'ı güncelle", "Update DNZ Launcher") : L.S("DNZ Launcher'ı kur", "Install DNZ Launcher"),
                        Theme.F(28, 2), Theme.Text, new RectangleF(X, 56, 460, 48), 0);
                    Theme.Draw(g, "v" + BuildInfo.Version + "  ·  " + L.S("Modern menüler, HUD ve yüksek FPS", "Modern menus, HUD and high FPS"),
                        Theme.F(13, 0), Theme.Muted, new RectangleF(X, 104, 460, 24), 0);
                    Theme.Draw(g, L.S("Kurulum konumu", "Install location"), Theme.F(12, 1), Theme.Muted, new RectangleF(X, 168, 400, 22), 0);
                    RectangleF box = new RectangleF(X, 196, 340, 44);
                    Theme.Fill(g, box, 10, Theme.Surface);
                    Theme.Draw(g, installDir, Theme.F(12, 0), Theme.Text, new RectangleF(box.X + 14, box.Y, box.Width - 28, box.Height), 0);
                    string note = update
                        ? L.S("Profillerin, dünyaların ve ayarların korunur.", "Your profiles, worlds and settings are kept.")
                        : L.S("Yönetici izni gerekmez  ·  Java dahil", "No admin rights needed  ·  Java included");
                    Theme.Draw(g, note, Theme.F(12, 0), Theme.Muted, new RectangleF(X, 336, 460, 24), 0);
                    break;

                case Step.Installing:
                    Theme.Draw(g, L.S("Kuruluyor", "Installing"), Theme.F(28, 2), Theme.Text, new RectangleF(X, 56, 460, 48), 0);
                    string pct = (int)Math.Round(shownProgress * 100) + "%";
                    Font big = Theme.F(54, 2);
                    float pw = g.MeasureString(pct, big).Width;
                    Theme.Draw(g, pct, big, Theme.Accent, new RectangleF(X - 6, 112, pw + 10, 80), 0);
                    Theme.Draw(g, status, Theme.F(13, 0), Theme.Muted, new RectangleF(X + pw + 4, 150, 460 - pw - 4, 34), 0);
                    RectangleF track = new RectangleF(X, 212, 460, 10);
                    Theme.Fill(g, track, 5, Theme.SurfaceHigh);
                    Theme.Fill(g, new RectangleF(track.X, track.Y, Math.Max(10f, track.Width * shownProgress), track.Height), 5, Theme.Accent);
                    Theme.Draw(g, L.S("Birkaç saniye sürer, pencereyi kapatma.", "Takes a few seconds, keep this window open."), Theme.F(12, 0), Theme.Muted,
                        new RectangleF(X, 236, 460, 24), 0);
                    break;

                case Step.Done:
                    Theme.Draw(g, update ? L.S("Güncelleme tamamlandı", "Update complete") : L.S("Kurulum tamamlandı", "Setup complete"),
                        Theme.F(28, 2), Theme.Text, new RectangleF(X, 56, 460, 48), 0);
                    Theme.Draw(g, L.S("DNZ Launcher kullanıma hazır.", "DNZ Launcher is ready to use."), Theme.F(13, 0), Theme.Muted,
                        new RectangleF(X, 104, 460, 24), 0);
                    Theme.Fill(g, new RectangleF(X, 170, 460, 56), 12, Theme.Surface);
                    break;

                case Step.Failed:
                    Theme.Draw(g, L.S("Kurulum yapılamadı", "Setup failed"), Theme.F(28, 2), Theme.Text, new RectangleF(X, 56, 460, 48), 0);
                    Theme.Paragraph(g, error, Theme.F(13, 1), Theme.Danger, new RectangleF(X, 116, 460, 200));
                    break;
            }
        }

        /// <summary>Left panel: rounded bars and dots, dark while setting up and orange when done.</summary>
        void Decoration(Graphics g, bool lit)
        {
            const float panel = 290;
            g.FillRectangle(new SolidBrush(Theme.Rgb(0x0D1118)), 0, 0, panel, H);
            using (Pen edge = new Pen(Theme.Border, 1f)) g.DrawLine(edge, panel, 0, panel, H);
            GraphicsState saved = g.Save();
            g.SetClip(new RectangleF(0, 0, panel, H));
            Color bar = lit ? Theme.Accent : Theme.Surface;
            Theme.Fill(g, new RectangleF(42, 156, 190, 48), 24, bar);
            float[] rows = { 218, 280, 342 };
            for (int i = 0; i < rows.Length; i++)
            {
                Color dot = lit ? (i == 1 ? Theme.Rgb(0x8A9099) : Theme.Rgb(0x555C66)) : Theme.Surface;
                Theme.Fill(g, new RectangleF(-24, rows[i], 48, 48), 24, dot);
                Theme.Fill(g, new RectangleF(42, rows[i], 280, 48), 24, bar);
            }
            g.Restore(saved);
        }

        static void Check(Graphics g, RectangleF r)
        {
            Theme.Fill(g, r, r.Width / 2, Theme.Alpha(Theme.Success, 40));
            using (Pen p = new Pen(Theme.Success, 3.2f))
            {
                p.StartCap = LineCap.Round;
                p.EndCap = LineCap.Round;
                p.LineJoin = LineJoin.Round;
                g.DrawLines(p, new PointF[] { new PointF(r.X + 13, r.Y + 23), new PointF(r.X + 19.5f, r.Y + 29.5f), new PointF(r.X + 31.5f, r.Y + 15.5f) });
            }
        }

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm());
        }
    }
}
