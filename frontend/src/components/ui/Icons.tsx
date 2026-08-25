import type { SVGProps } from 'react';

/**
 * The application's icon set, drawn inline.
 *
 * Hand-drawn rather than pulled from an icon package: the application needs
 * around fifteen glyphs, and a library would add a dependency and a bundle for
 * shapes that are four path commands each. Inline SVG also inherits
 * `currentColor`, so icons follow their surrounding text through a theme switch
 * with no extra work.
 *
 * All are marked `aria-hidden`: every one sits beside a text label or inside a
 * button with an `aria-label`, so announcing them would only produce duplicate
 * noise for a screen reader.
 */

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Icon({ size = 18, children, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const IconChart = (props: IconProps) => (
  <Icon {...props}>
    <path d="M3 3v18h18" />
    <path d="m7 14 4-4 3 3 5-6" />
  </Icon>
);

export const IconReceipt = (props: IconProps) => (
  <Icon {...props}>
    <path d="M4 2v20l3-2 3 2 3-2 3 2 3-2V2l-3 2-3-2-3 2-3-2Z" />
    <path d="M8 8h8M8 12h8M8 16h4" />
  </Icon>
);

export const IconTag = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12.6 2.6a2 2 0 0 0-1.4-.6H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.2 8.2a2 2 0 0 0 2.8 0l7.2-7.2a2 2 0 0 0 0-2.8Z" />
    <circle cx="7" cy="7" r="1.4" />
  </Icon>
);

export const IconUsers = (props: IconProps) => (
  <Icon {...props}>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
  </Icon>
);

export const IconUser = (props: IconProps) => (
  <Icon {...props}>
    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </Icon>
);

export const IconLogout = (props: IconProps) => (
  <Icon {...props}>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="m16 17 5-5-5-5M21 12H9" />
  </Icon>
);

export const IconMenu = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 20}>
    <path d="M3 6h18M3 12h18M3 18h18" />
  </Icon>
);

export const IconClose = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 20}>
    <path d="M18 6 6 18M6 6l12 12" />
  </Icon>
);

export const IconSun = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 20}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </Icon>
);

export const IconMoon = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 20}>
    <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
  </Icon>
);

export const IconPlus = (props: IconProps) => (
  <Icon {...props}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);

export const IconEdit = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 16}>
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4Z" />
  </Icon>
);

export const IconTrash = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 16}>
    <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
    <path d="M10 11v6M14 11v6" />
  </Icon>
);

export const IconEye = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 16}>
    <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z" />
    <circle cx="12" cy="12" r="3" />
  </Icon>
);

export const IconArrowLeft = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 16}>
    <path d="M19 12H5M12 19l-7-7 7-7" />
  </Icon>
);

export const IconFilter = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 16}>
    <path d="M22 3H2l8 9.5V19l4 2v-8.5L22 3Z" />
  </Icon>
);

export const IconWallet = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 22}>
    <path d="M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0 0 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5" />
    <path d="M17 12h.01" />
  </Icon>
);

export const IconTrendUp = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 22}>
    <path d="M22 7 13.5 15.5 8.5 10.5 2 17" />
    <path d="M16 7h6v6" />
  </Icon>
);

export const IconTrendDown = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 22}>
    <path d="M22 17 13.5 8.5 8.5 13.5 2 7" />
    <path d="M16 17h6v-6" />
  </Icon>
);

export const IconShield = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 22}>
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
    <path d="m9 12 2 2 4-4" />
  </Icon>
);

export const IconInbox = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 24}>
    <path d="M22 12h-6l-2 3h-4l-2-3H2" />
    <path d="M5.5 5.1 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.8 4H7.2a2 2 0 0 0-1.7 1.1Z" />
  </Icon>
);

export const IconCheck = (props: IconProps) => (
  <Icon {...props} size={props.size ?? 16}>
    <path d="M20 6 9 17l-5-5" />
  </Icon>
);
