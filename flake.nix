{
  description = "Cobblemon unchained dev env";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let pkgs = nixpkgs.legacyPackages.${system};
      in {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk21
            gradle
            libGL
            libGLU
            xorg.libX11
            xorg.libXext
            xorg.libXrender
            xorg.libXrandr
            xorg.libXxf86vm
            xorg.libXi
            xorg.libXcursor
            xorg.libXinerama
            alsa-lib
            pulseaudio
          ];

          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
            pkgs.libGL
            pkgs.libGLU
            pkgs.xorg.libX11
            pkgs.xorg.libXext
            pkgs.xorg.libXxf86vm
            pkgs.xorg.libXi
            pkgs.xorg.libXcursor
            pkgs.xorg.libXinerama
            pkgs.alsa-lib
            pkgs.pulseaudio
          ];
        };
      });
}
