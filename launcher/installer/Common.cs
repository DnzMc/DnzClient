// DNZ Launcher installer: shared look (the launcher's colors, hero, buttons) for Setup and Uninstall.
// Plain C# 5 so it builds with the compiler that ships with every Windows (.NET Framework 4.8).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Globalization;
using System.IO;
using System.Management;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace Dnz
{
    static class L
    {
        /// <summary>
        /// Turkish or English. Starts from the language the setup was last used in (saved by the setup), else from
        /// Windows (language or region Turkish); the TR / EN switch at the top changes it.
        /// </summary>
        public static bool Turkish = Detect();

        static bool Detect()
        {
            try
            {
                using (Microsoft.Win32.RegistryKey k = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(DnzForm.UninstallKey))
                {
                    object saved = k == null ? null : k.GetValue("InstallerLanguage");
                    if (saved != null) return saved.ToString() == "TR";
                }
            }
            catch { }
            return CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "tr" || CultureInfo.CurrentCulture.TwoLetterISOLanguageName == "tr";
        }

        public static string S(string tr, string en)
        {
            return Turkish ? tr : en;
        }
    }

    static class Theme
    {
        public static readonly Color Bg = Rgb(0x0A0D16);
        public static readonly Color Surface = Rgb(0x131826);
        public static readonly Color SurfaceHigh = Rgb(0x1A2133);
        public static readonly Color Border = Color.FromArgb(0x22, 255, 255, 255);
        public static readonly Color Accent = Rgb(0x4FA3FF);
        public static readonly Color Accent2 = Rgb(0x7B5CFF);
        public static readonly Color Text = Rgb(0xF2F4FA);
        public static readonly Color Muted = Rgb(0x8C95AB);
        public static readonly Color Danger = Rgb(0xFF6B6B);
        public static readonly Color Success = Rgb(0x7CE38B);

        static readonly Dictionary<string, Font> fonts = new Dictionary<string, Font>();

        public static Color Rgb(int rgb)
        {
            return Color.FromArgb(255, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        }

        public static Color Alpha(Color c, int a)
        {
            return Color.FromArgb(a, c.R, c.G, c.B);
        }

        public static Color Lerp(Color a, Color b, float t)
        {
            t = Math.Max(0f, Math.Min(1f, t));
            return Color.FromArgb((int)(a.A + (b.A - a.A) * t), (int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
        }

        /// <summary>Segoe UI in pixels (weight: 0 normal, 1 semibold, 2 bold).</summary>
        public static Font F(float px, int weight)
        {
            string key = px + ":" + weight;
            Font f;
            if (!fonts.TryGetValue(key, out f))
            {
                string family = weight == 1 ? "Segoe UI Semibold" : "Segoe UI";
                f = new Font(family, px, weight == 2 ? FontStyle.Bold : FontStyle.Regular, GraphicsUnit.Pixel);
                fonts[key] = f;
            }
            return f;
        }

        public static GraphicsPath Round(RectangleF r, float radius)
        {
            float d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
            GraphicsPath p = new GraphicsPath();
            if (d <= 0.5f)
            {
                p.AddRectangle(r);
                return p;
            }
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        public static void Fill(Graphics g, RectangleF r, float radius, Brush brush)
        {
            using (GraphicsPath p = Round(r, radius)) g.FillPath(brush, p);
        }

        public static void Fill(Graphics g, RectangleF r, float radius, Color color)
        {
            using (SolidBrush b = new SolidBrush(color)) Fill(g, r, radius, b);
        }

        public static void Outline(Graphics g, RectangleF r, float radius, Color color, float width)
        {
            using (GraphicsPath p = Round(r, radius))
            using (Pen pen = new Pen(color, width)) g.DrawPath(pen, p);
        }

        public static LinearGradientBrush AccentBrush(RectangleF r, Color from, Color to)
        {
            return new LinearGradientBrush(new RectangleF(r.X - 1, r.Y, r.Width + 2, r.Height), from, to, LinearGradientMode.Horizontal);
        }

        /// <summary>Text in a box: [align] 0 left, 1 center, 2 right; vertically centered, cut with "..." when too long.</summary>
        public static void Draw(Graphics g, string text, Font font, Color color, RectangleF box, int align)
        {
            using (StringFormat sf = new StringFormat(StringFormat.GenericTypographic))
            using (SolidBrush b = new SolidBrush(color))
            {
                sf.FormatFlags |= StringFormatFlags.NoWrap | StringFormatFlags.MeasureTrailingSpaces;
                sf.Alignment = align == 0 ? StringAlignment.Near : align == 1 ? StringAlignment.Center : StringAlignment.Far;
                sf.LineAlignment = StringAlignment.Center;
                sf.Trimming = StringTrimming.EllipsisCharacter;
                g.DrawString(text, font, b, box, sf);
            }
        }

        /// <summary>Wrapped text starting at the top of [box].</summary>
        public static void Paragraph(Graphics g, string text, Font font, Color color, RectangleF box)
        {
            using (StringFormat sf = new StringFormat())
            using (SolidBrush b = new SolidBrush(color))
            {
                sf.Trimming = StringTrimming.EllipsisWord;
                g.DrawString(text, font, b, box, sf);
            }
        }

        /// <summary>The launcher's hero: blue -> purple gradient with soft pixel squares, fading into the background.</summary>
        public static void Hero(Graphics g, RectangleF r)
        {
            using (LinearGradientBrush b = new LinearGradientBrush(r, Rgb(0x1B3F8F), Rgb(0x5B1F7A), LinearGradientMode.ForwardDiagonal))
            {
                ColorBlend blend = new ColorBlend();
                blend.Colors = new Color[] { Rgb(0x1B3F8F), Rgb(0x3A2A8C), Rgb(0x5B1F7A) };
                blend.Positions = new float[] { 0f, 0.5f, 1f };
                b.InterpolationColors = blend;
                g.FillRectangle(b, r);
            }
            Random rnd = new Random(26);
            const float cell = 46f;
            for (float y = r.Y; y < r.Bottom; y += cell)
            {
                for (float x = r.X; x < r.Right; x += cell)
                {
                    if (rnd.NextDouble() < 0.34)
                    {
                        using (SolidBrush sq = new SolidBrush(Color.FromArgb(6 + rnd.Next(24), 255, 255, 255)))
                            g.FillRectangle(sq, x, y, cell, cell);
                    }
                }
            }
            RectangleF fade = new RectangleF(r.X, r.Bottom - 110, r.Width, 111);
            // The brush is a little taller than the area, so GDI+ never draws its wrap-around line at the top edge.
            RectangleF brushArea = new RectangleF(fade.X, fade.Y - 2, fade.Width, fade.Height + 4);
            using (LinearGradientBrush f = new LinearGradientBrush(brushArea, Alpha(Bg, 0), Bg, LinearGradientMode.Vertical))
                g.FillRectangle(f, fade);
        }

        static Image logo;

        public static Image Logo()
        {
            if (logo == null)
            {
                Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("logo.png");
                if (s != null) logo = Image.FromStream(s);
            }
            return logo;
        }

        public static void DrawLogo(Graphics g, RectangleF r, float radius)
        {
            Image img = Logo();
            if (img == null) return;
            using (GraphicsPath p = Round(r, radius))
            {
                GraphicsState st = g.Save();
                g.SetClip(p);
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g.DrawImage(img, r);
                g.Restore(st);
            }
        }
    }

    /// <summary>A clickable area drawn by the window. Kind: primary (gradient), secondary, link, toggle, window, close.</summary>
    class Btn
    {
        public enum Kind { Primary, Danger, Secondary, Link, Toggle, Window, Close, Lang }

        public RectangleF Rect;
        public string Label;
        public Kind Type;
        public bool Enabled = true;
        public bool Checked;
        public Action Click;
        public float Hover;

        public Btn(Kind type, RectangleF rect, string label, Action click)
        {
            Type = type;
            Rect = rect;
            Label = label;
            Click = click;
        }
    }

    /// <summary>Borderless, rounded, DPI-aware window in the launcher's style; subclasses paint in 1/96-inch units.</summary>
    class DnzForm : Form
    {
        [DllImport("dwmapi.dll")]
        static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

        [DllImport("user32.dll")]
        static extern bool ReleaseCapture();

        [DllImport("user32.dll")]
        static extern IntPtr SendMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);

        protected float Zoom = 1f;
        protected readonly int W;
        protected readonly int H;
        protected List<Btn> Buttons = new List<Btn>();
        Btn hovered;
        Btn pressed;
        readonly Timer timer = new Timer();

        public DnzForm(int width, int height, string title)
        {
            W = width;
            H = height;
            Text = title;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Theme.Bg;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            using (Graphics g = CreateGraphics()) Zoom = g.DpiX / 96f;
            ClientSize = new Size((int)(width * Zoom), (int)(height * Zoom));
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            timer.Interval = 15;
            timer.Tick += delegate { Animate(); };
            timer.Start();
        }

        protected override CreateParams CreateParams
        {
            get
            {
                CreateParams cp = base.CreateParams;
                cp.ClassStyle |= 0x20000; // drop shadow
                cp.Style |= 0x20000; // minimize from the taskbar
                return cp;
            }
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            try
            {
                int round = 2; // Windows 11: rounded corners
                DwmSetWindowAttribute(Handle, 33, ref round, 4);
            }
            catch { }
        }

        /// <summary>Sets the window's buttons for the current step; minimize and close are always there.</summary>
        protected void SetButtons(params Btn[] buttons)
        {
            Buttons = new List<Btn>(buttons);
            // Language switch next to the window buttons.
            Btn tr = new Btn(Btn.Kind.Lang, new RectangleF(W - 176, 7, 38, 22), "TR", delegate { SetLanguage(true); });
            tr.Checked = L.Turkish;
            Btn en = new Btn(Btn.Kind.Lang, new RectangleF(W - 136, 7, 38, 22), "EN", delegate { SetLanguage(false); });
            en.Checked = !L.Turkish;
            Buttons.Add(tr);
            Buttons.Add(en);
            Buttons.Add(new Btn(Btn.Kind.Window, new RectangleF(W - 92, 0, 46, 36), "—", delegate { WindowState = FormWindowState.Minimized; }));
            Buttons.Add(new Btn(Btn.Kind.Close, new RectangleF(W - 46, 0, 46, 36), "✕", delegate { if (CanClose()) Close(); }));
            hovered = null;
            pressed = null;
            Invalidate();
        }

        void SetLanguage(bool turkish)
        {
            if (L.Turkish == turkish) return;
            L.Turkish = turkish;
            Relabel();
            Invalidate();
        }

        /// <summary>After a language change: the window sets its buttons again with the new texts.</summary>
        protected virtual void Relabel()
        {
        }

        protected virtual bool CanClose()
        {
            return true;
        }

        /// <summary>Called ~60 times a second: hover fades, and subclasses can animate.</summary>
        protected virtual bool Tick()
        {
            return false;
        }

        void Animate()
        {
            bool changed = Tick();
            foreach (Btn b in Buttons)
            {
                float target = b == hovered && b.Enabled ? 1f : 0f;
                if (Math.Abs(b.Hover - target) > 0.01f)
                {
                    b.Hover += (target - b.Hover) * 0.25f;
                    changed = true;
                }
                else b.Hover = target;
            }
            if (changed) Invalidate();
        }

        PointF Logical(Point p)
        {
            return new PointF(p.X / Zoom, p.Y / Zoom);
        }

        Btn Hit(PointF p)
        {
            for (int i = Buttons.Count - 1; i >= 0; i--)
                if (Buttons[i].Rect.Contains(p)) return Buttons[i];
            return null;
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            hovered = Hit(Logical(e.Location));
            Cursor = hovered != null && hovered.Enabled ? Cursors.Hand : Cursors.Default;
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            hovered = null;
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button != MouseButtons.Left) return;
            pressed = Hit(Logical(e.Location));
            if (pressed == null)
            {
                // Drag the window from anywhere else.
                ReleaseCapture();
                SendMessage(Handle, 0xA1, (IntPtr)2, IntPtr.Zero);
            }
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            base.OnMouseUp(e);
            Btn b = Hit(Logical(e.Location));
            if (b != null && b == pressed && b.Enabled && b.Click != null) b.Click();
            pressed = null;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.ScaleTransform(Zoom, Zoom);
            g.Clear(Theme.Bg);
            PaintContent(g);
            foreach (Btn b in Buttons) DrawButton(g, b);
            using (Pen edge = new Pen(Theme.Border, 1f)) g.DrawRectangle(edge, 0.5f, 0.5f, W - 1, H - 1);
        }

        protected virtual void PaintContent(Graphics g)
        {
        }

        void DrawButton(Graphics g, Btn b)
        {
            RectangleF r = b.Rect;
            switch (b.Type)
            {
                case Btn.Kind.Primary:
                case Btn.Kind.Danger:
                {
                    Color a = b.Type == Btn.Kind.Danger ? Theme.Rgb(0xFF6B6B) : Theme.Accent;
                    Color c = b.Type == Btn.Kind.Danger ? Theme.Rgb(0xD9365E) : Theme.Accent2;
                    if (!b.Enabled)
                    {
                        a = Theme.SurfaceHigh;
                        c = Theme.SurfaceHigh;
                    }
                    using (LinearGradientBrush br = Theme.AccentBrush(r, a, c)) Theme.Fill(g, r, 14, br);
                    if (b.Hover > 0) Theme.Fill(g, r, 14, Color.FromArgb((int)(56 * b.Hover), 255, 255, 255));
                    Theme.Draw(g, b.Label, Theme.F(r.Height > 44 ? 17 : 14, 2), b.Enabled ? Color.White : Theme.Muted, r, 1);
                    break;
                }
                case Btn.Kind.Secondary:
                    Theme.Fill(g, r, 10, Theme.Lerp(Theme.Surface, Theme.SurfaceHigh, b.Hover));
                    Theme.Outline(g, r, 10, Theme.Border, 1f);
                    Theme.Draw(g, b.Label, Theme.F(13, 1), Theme.Text, r, 1);
                    break;
                case Btn.Kind.Lang:
                    if (b.Checked) Theme.Fill(g, r, 7, Color.FromArgb(70, 255, 255, 255));
                    else if (b.Hover > 0) Theme.Fill(g, r, 7, Color.FromArgb((int)(30 * b.Hover), 255, 255, 255));
                    Theme.Draw(g, b.Label, Theme.F(11, 2), b.Checked ? Color.White : Color.FromArgb(170, 255, 255, 255), r, 1);
                    break;
                case Btn.Kind.Link:
                    Theme.Draw(g, b.Label, Theme.F(13, 1), Theme.Lerp(Theme.Muted, Theme.Text, b.Hover), r, 1);
                    break;
                case Btn.Kind.Toggle:
                {
                    RectangleF box = new RectangleF(r.X, r.Y + (r.Height - 18) / 2, 18, 18);
                    if (b.Checked)
                    {
                        using (LinearGradientBrush br = Theme.AccentBrush(box, Theme.Accent, Theme.Accent2)) Theme.Fill(g, box, 5, br);
                        using (Pen p = new Pen(Color.White, 2f))
                        {
                            p.StartCap = LineCap.Round;
                            p.EndCap = LineCap.Round;
                            g.DrawLines(p, new PointF[] { new PointF(box.X + 4.5f, box.Y + 9.5f), new PointF(box.X + 7.8f, box.Y + 12.8f), new PointF(box.X + 13.8f, box.Y + 5.8f) });
                        }
                    }
                    else
                    {
                        Theme.Fill(g, box, 5, Theme.Lerp(Theme.Surface, Theme.SurfaceHigh, b.Hover));
                        Theme.Outline(g, box, 5, Color.FromArgb(0x55, 255, 255, 255), 1.2f);
                    }
                    Theme.Draw(g, b.Label, Theme.F(13, 0), Theme.Lerp(Theme.Muted, Theme.Text, b.Checked ? 1f : b.Hover), new RectangleF(r.X + 26, r.Y, r.Width - 26, r.Height), 0);
                    break;
                }
                case Btn.Kind.Window:
                case Btn.Kind.Close:
                {
                    Color bg = b.Type == Btn.Kind.Close ? Theme.Rgb(0xC42B1C) : Color.FromArgb(40, 255, 255, 255);
                    if (b.Hover > 0) g.FillRectangle(new SolidBrush(Color.FromArgb((int)(bg.A * b.Hover), bg.R, bg.G, bg.B)), r);
                    Theme.Draw(g, b.Label, Theme.F(12, 0), Color.White, r, 1);
                    break;
                }
            }
        }

        /// <summary>True if any DNZ Launcher runs (installed or not): it holds files open and would overwrite settings.</summary>
        public static bool LauncherRunning()
        {
            try
            {
                using (ManagementObjectSearcher s = new ManagementObjectSearcher("SELECT CommandLine FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe'"))
                {
                    foreach (ManagementBaseObject p in s.Get())
                    {
                        object cmd = p["CommandLine"];
                        if (cmd != null && cmd.ToString().Contains("dnz.launcher.MainKt")) return true;
                    }
                }
            }
            catch { }
            return false;
        }

        public static string Programs()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs");
        }

        public static string DesktopLink()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "DNZ Launcher.lnk");
        }

        public static string StartMenuLink()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "DNZ Launcher.lnk");
        }

        public static string StartupLink()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Startup), "DNZ Launcher.lnk");
        }

        public const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\DNZLauncher";
    }
}
