const fs = require("node:fs");
const path = require("node:path");
const sharp = require("sharp");

async function main() {
  const root = path.resolve(__dirname, "..");
  const source = path.join(root, "build", "app-icon.svg");
  const pngPath = path.join(root, "build", "icon.png");
  const icoPath = path.join(root, "build", "icon.ico");
  const png = await sharp(source, { density: 384 })
    .resize(256, 256, { fit: "cover" })
    .png({ compressionLevel: 9 })
    .toBuffer();

  // ICO header followed by a 256x256 PNG (Windows Vista and newer).
  const header = Buffer.alloc(22);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(1, 4);
  header.writeUInt8(0, 6);
  header.writeUInt8(0, 7);
  header.writeUInt8(0, 8);
  header.writeUInt8(0, 9);
  header.writeUInt16LE(1, 10);
  header.writeUInt16LE(32, 12);
  header.writeUInt32LE(png.length, 14);
  header.writeUInt32LE(22, 18);
  fs.writeFileSync(pngPath, png);
  fs.writeFileSync(icoPath, Buffer.concat([header, png]));
  console.log("Icono generado:", icoPath, "(" + (png.length + 22) + " bytes)");
}
main().catch(error => {
  console.error("No se pudo generar el icono:", error);
  process.exitCode = 1;
});
