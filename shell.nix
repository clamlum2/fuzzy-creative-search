{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell {
  packages = with pkgs; [
    jdk25
    mesa
    libGL
    libGLU
    libglvnd
    glfw
    libX11
    libXext
    libXrandr
    libXcursor
    libXi
    libXxf86vm
  ];

  shellHook = ''
    export LD_LIBRARY_PATH=${pkgs.lib.makeLibraryPath [
      pkgs.mesa pkgs.libGL pkgs.libGLU pkgs.libglvnd pkgs.glfw
      pkgs.libX11 pkgs.libXext pkgs.libXrandr
      pkgs.libXcursor pkgs.libXi pkgs.libXxf86vm
    ]}:$LD_LIBRARY_PATH
  '';
}
