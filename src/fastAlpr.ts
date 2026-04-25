import { Asset } from "expo-asset";
import * as FileSystem from "expo-file-system";
import * as ort from "onnxruntime-react-native";

export type FastAlprPlate = {
  plate: string;
  region: string;
  confidence: number;
};

export type FastAlprResult = {
  results: FastAlprPlate[];
};

type FastAlprModelConfig = {
  detectorModel?: number | string;
  ocrModel?: number | string;
};

const CACHE_PREFIX = "fastalpr.result";
const CACHE_DIRECTORY = FileSystem.Paths.cache.uri;

let modelConfig: FastAlprModelConfig = {};
let sessions: Promise<{
  detector?: ort.InferenceSession;
  ocr?: ort.InferenceSession;
}> | null = null;

export function configureFastAlpr(config: FastAlprModelConfig) {
  modelConfig = config;
  sessions = null;
}

async function assetToLocalUri(source: number | string) {
  if (typeof source === "string") {
    return source;
  }

  const asset = Asset.fromModule(source);
  await asset.downloadAsync();
  return asset.localUri ?? asset.uri;
}

async function getSessions() {
  if (!modelConfig.detectorModel || !modelConfig.ocrModel) {
    return {};
  }

  sessions ??= Promise.all([
    assetToLocalUri(modelConfig.detectorModel),
    assetToLocalUri(modelConfig.ocrModel)
  ]).then(async ([detectorPath, ocrPath]) => ({
    detector: await ort.InferenceSession.create(detectorPath),
    ocr: await ort.InferenceSession.create(ocrPath)
  }));

  return sessions;
}

async function cacheKey(file: any) {
  const uri = file.uri || file.url;
  const info = await FileSystem.getInfoAsync(uri, { md5: true });
  return `${CACHE_PREFIX}.${(info as any).md5 ?? uri}`;
}

async function runFastAlpr(file: any): Promise<FastAlprResult> {
  const { detector, ocr } = await getSessions();

  if (!detector || !ocr) {
    console.warn(
      "FastALPR ONNX models are not configured. Bundle detector and OCR models, then call configureFastAlpr()."
    );
    return { results: [] };
  }

  // The native runtime is wired here. The Python fast-alpr package is not usable
  // inside React Native, so detector/OCR preprocessing and postprocessing must be
  // implemented against the exact ONNX models bundled with the app.
  void file;
  void detector;
  void ocr;
  return { results: [] };
}

export const alpr = {
  recognize: async (file: any): Promise<FastAlprResult> => {
    const key = await cacheKey(file);
    const cached = await FileSystem.readAsStringAsync(
      `${CACHE_DIRECTORY}${encodeURIComponent(key)}.json`
    ).catch(() => null);

    if (cached) {
      return JSON.parse(cached);
    }

    const result = await runFastAlpr(file);
    await FileSystem.writeAsStringAsync(
      `${CACHE_DIRECTORY}${encodeURIComponent(key)}.json`,
      JSON.stringify(result)
    ).catch(() => undefined);
    return result;
  }
};
