import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useState, useRef, useEffect } from "react";
import { toast } from "sonner";

interface VoiceDemoModalProps {
  onClose: () => void;
}

type DemoState = "IDLE" | "LISTENING" | "PROCESSING" | "RESPONDING" | "ERROR";

const VoiceDemoModal = ({ onClose }: VoiceDemoModalProps) => {
  const [state, setState] = useState<DemoState>("IDLE");
  const [errorMsg, setErrorMsg] = useState<string>("");
  const [messages, setMessages] = useState<{role: string, content: string}[]>([]);
  const messagesRef = useRef<{role: string, content: string}[]>([]);
  const sessionIdRef = useRef<string | null>(null);
  
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const [volume, setVolume] = useState<number>(0);
  
  const isContinuousRef = useRef(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<BlobPart[]>([]);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const lastSpokeTimeRef = useRef<number>(Date.now());
  const isSpeakingRef = useRef<boolean>(false);
  const animationFrameRef = useRef<number>(0);
  const audioPlaybackRef = useRef<HTMLAudioElement | null>(null);

  // Setup loop
  const startInteraction = async () => {
    isContinuousRef.current = true;
    startRecording();
  };

  const startRecording = async () => {
    if (!isContinuousRef.current) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];
      
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      audioContextRef.current = audioContext;
      const source = audioContext.createMediaStreamSource(stream);
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.5;
      source.connect(analyser);
      analyserRef.current = analyser;

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      mediaRecorder.onstop = async () => {
        cleanupAudio();
        if (!isContinuousRef.current) return;
        
        const mimeType = mediaRecorder.mimeType || "audio/webm";
        const extension = mimeType.includes("mp4") ? "m4a" : mimeType.includes("ogg") ? "ogg" : "webm";
        const audioBlob = new Blob(audioChunksRef.current, { type: mimeType });
        await processAudio(audioBlob, extension);
      };

      mediaRecorder.start();
      setState("LISTENING");
      lastSpokeTimeRef.current = Date.now();
      isSpeakingRef.current = false;
      
      monitorAudio();
      
    } catch (error) {
      console.error("Mic error:", error);
      setState("ERROR");
      setErrorMsg("Microphone access denied. Please allow access to use the continuous demo.");
      stopInteraction();
    }
  };

  const monitorAudio = () => {
    if (!analyserRef.current || !isContinuousRef.current || state !== "LISTENING" && state !== "IDLE") return;

    const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
    analyserRef.current.getByteFrequencyData(dataArray);
    
    // Calculate average volume
    let sum = 0;
    for (let i = 0; i < dataArray.length; i++) {
        sum += dataArray[i];
    }
    const currentVolume = sum / dataArray.length;
    setVolume(currentVolume);

    const minVolumeThreshold = 25; // Increased threshold to avoid ambient background noise
    const silenceDelayMs = 2000; // Increased to 2 seconds of silence to prevent premature cutoff

    if (currentVolume > minVolumeThreshold) {
        lastSpokeTimeRef.current = Date.now();
        isSpeakingRef.current = true;
    } else if (isSpeakingRef.current) {
        const timeSinceLastSpoke = Date.now() - lastSpokeTimeRef.current;
        if (timeSinceLastSpoke > silenceDelayMs) {
            // User finished speaking.
            if (mediaRecorderRef.current && mediaRecorderRef.current.state === "recording") {
                mediaRecorderRef.current.stop();
                return; // Stop animation loop
            }
        }
    } else {
        // Handle case where user never spoke at all, time out after a longer period to prompt them again
        const timeSinceStart = Date.now() - lastSpokeTimeRef.current;
        if (timeSinceStart > 15000 && mediaRecorderRef.current && mediaRecorderRef.current.state === "recording") {
             // 15 seconds without ANY sound
             mediaRecorderRef.current.stop();
             return;
        }
    }

    animationFrameRef.current = requestAnimationFrame(monitorAudio);
  };

  const cleanupAudio = () => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach(track => track.stop());
    }
    if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
      audioContextRef.current.close().catch(e => console.error("Audio context close error:", e));
    }
  };

  const stopInteraction = () => {
    isContinuousRef.current = false;
    sessionIdRef.current = null;
    cleanupAudio();
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === "recording") {
      mediaRecorderRef.current.stop();
    }
    if (audioPlaybackRef.current) {
      audioPlaybackRef.current.pause();
    }
    setState("IDLE");
    setVolume(0);
  };

  // cleanup on unmount
  useEffect(() => {
    return () => {
      stopInteraction();
    };
  }, []);

  const processAudio = async (audioBlob: Blob, extension: string) => {
    setState("PROCESSING");
    
    try {
      const formData = new FormData();
      formData.append("audio", audioBlob, "recording." + extension);
      if (sessionIdRef.current) {
          formData.append("sessionId", sessionIdRef.current);
      }
      
      const apiBase = import.meta.env.VITE_API_URL || 'http://localhost:8081';
      const response = await fetch(`${apiBase}/api/voice`, {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error("Failed to process request");
      }

      const data = await response.json();
      
      if (data.sessionId) {
          sessionIdRef.current = data.sessionId;
      }

      let currentTurnMessages = [...messagesRef.current];
      
      if (data.text) {
        if (data.userText && data.userText !== "[Silence]" && data.userText.trim() !== "") {
           currentTurnMessages.push({role: "user", content: data.userText});
        }
        currentTurnMessages.push({role: "assistant", content: data.text});
        setMessages(currentTurnMessages);
      }
      
      if (!isContinuousRef.current) return;

      if (data.audio) {
        setState("RESPONDING");
        try {
          const audio = new Audio("data:audio/wav;base64," + data.audio);
          audioPlaybackRef.current = audio;
          audio.onended = () => {
            // Automatically loop back to recording once finished
            if (isContinuousRef.current) {
                if (data.endCall) {
                    setTimeout(() => stopInteraction(), 1000); // Small delay to let user absorb
                } else {
                    startRecording();
                }
            }
          };
          await audio.play();
        } catch (playError) {
          console.error("Audio play error", playError);
          // If playback fails, keep going loop
          if (isContinuousRef.current) {
              if (data.endCall) stopInteraction();
              else startRecording();
          }
        }
      } else {
        // No audio returned, jump back to listening
        if (isContinuousRef.current) {
            if (data.endCall) stopInteraction();
            else startRecording();
        }
      }
      
    } catch (error) {
      console.error("API error:", error);
      if (isContinuousRef.current) {
          setState("ERROR");
          setErrorMsg("Failed to connect to the Voice API. Backend may be unreachable.");
          stopInteraction();
      }
    }
  };

  const scaleValue = Math.min(1 + (volume / 40), 1.7); // Scale bubble size

  // Optional auto-scroll for chat log
  const chatContainerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
     if (chatContainerRef.current) {
         chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
     }
  }, [messages, state]);

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
      <Card className="w-full max-w-xl shadow-2xl flex flex-col h-[600px] border-0 rounded-3xl overflow-hidden pointer-events-auto filter-none mix-blend-normal transform-none">
        <div className="bg-gray-900 px-6 py-4 flex justify-between items-center shrink-0">
          <div className="flex items-center gap-3">
             <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center">
                <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"/></svg>
             </div>
             <h2 className="text-xl font-semibold text-white">Continuous Voice AI</h2>
          </div>
          <Button 
            variant="ghost" 
            onClick={() => { stopInteraction(); onClose(); }}
            className="text-gray-400 hover:text-white hover:bg-gray-800 rounded-full h-10 w-10 p-0"
          >
            ✕
          </Button>
        </div>
        
        <CardContent className="p-6 flex flex-col h-full overflow-hidden bg-white">
          <div ref={chatContainerRef} className="flex-1 overflow-y-auto mb-6 bg-slate-50/50 rounded-2xl p-4 flex flex-col gap-4 border border-slate-100 relative">
             {messages.length === 0 && state === "IDLE" && (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center text-slate-400 font-medium gap-3">
                    <svg className="w-12 h-12 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/></svg>
                    Click Start below to begin.<br/>I'll listen whenever you speak!
                </div>
             )}
             
             {messages.map((msg, index) => (
               <div key={index} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} w-full`}>
                 <div className={`max-w-[85%] rounded-2xl p-3.5 ${msg.role === 'user' ? 'bg-blue-600 text-white rounded-br-sm shadow-md leading-relaxed break-words' : 'bg-white text-slate-700 border border-slate-200 rounded-bl-sm shadow-sm leading-relaxed break-words'}`}>
                   {msg.content}
                 </div>
               </div>
             ))}
             
             {/* Typing Indicator for processing state */}
             {state === "PROCESSING" && (
                 <div className="flex justify-start w-full">
                     <div className="max-w-[40%] bg-white border border-slate-200 rounded-2xl rounded-bl-sm shadow-sm p-4 flex gap-1.5 items-center">
                         <div className="w-2 h-2 bg-slate-300 rounded-full animate-bounce" style={{animationDelay: "0ms"}}></div>
                         <div className="w-2 h-2 bg-slate-300 rounded-full animate-bounce" style={{animationDelay: "150ms"}}></div>
                         <div className="w-2 h-2 bg-slate-300 rounded-full animate-bounce" style={{animationDelay: "300ms"}}></div>
                     </div>
                 </div>
             )}
          </div>

          <div className="mt-auto shrink-0 flex flex-col justify-center items-center py-2 h-[120px]">
            {state === "IDLE" && (
              <Button 
                size="lg" 
                onClick={startInteraction}
                className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-7 rounded-full shadow-lg text-lg font-medium w-full flex gap-3 transition-colors"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"/></svg>
                Start Conversation
              </Button>
            )}

            {state === "LISTENING" && (
              <div className="flex w-full items-center justify-between bg-blue-50/80 p-3 rounded-3xl border border-blue-100">
                <div className="flex-1 flex justify-center items-center h-16 w-32 relative">
                    <div 
                      className={`absolute w-12 h-12 bg-blue-200/50 rounded-full transition-transform duration-75`}
                      style={{ transform: `scale(${scaleValue})` }}
                    ></div>
                    <div className={`relative z-10 w-12 h-12 rounded-full flex items-center justify-center ${volume > 10 ? 'bg-blue-600 text-white' : 'bg-blue-100 text-blue-500'}`}>
                      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5-3c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>
                    </div>
                </div>
                <div className="flex-1 text-center font-medium text-blue-700">
                    {volume > 10 ? "Listening..." : "Listening..."}
                </div>
                <div className="flex-1 flex justify-end pr-2">
                    <Button variant="outline" size="sm" className="rounded-full border-red-200 text-red-600 hover:bg-red-50 z-50 pointer-events-auto" onClick={stopInteraction}>Stop</Button>
                </div>
              </div>
            )}

            {(state === "PROCESSING" || state === "RESPONDING") && (
              <div className="flex w-full items-center justify-between bg-slate-50 p-3 rounded-3xl border border-slate-200">
                <div className="flex-1 flex justify-center items-center h-16 w-32 relative">
                   <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center animate-pulse">
                      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
                    </div>
                </div>
                <div className="flex-1 text-center font-medium text-slate-700">
                    {state === "PROCESSING" ? "Analyzing..." : "Speaking..."}
                </div>
                <div className="flex-1 flex justify-end pr-2">
                    <Button variant="outline" size="sm" className="rounded-full border-red-200 text-red-600 hover:bg-red-50 z-50 pointer-events-auto" onClick={stopInteraction}>Stop</Button>
                </div>
              </div>
            )}

            {state === "ERROR" && (
               <div className="flex w-full flex-col gap-3">
                   <div className="text-center text-red-500 font-medium px-4">{errorMsg}</div>
                   <Button onClick={startInteraction} className="w-full h-12 rounded-2xl bg-slate-900 text-white">Restart</Button>
               </div>
            )}
          </div>
          
        </CardContent>
      </Card>
    </div>
  );
};

export default VoiceDemoModal;
