import Navbar from "./Navbar";
import Hero from "./Hero";
import { ValueStrip, Features, PatientDoctorSection } from "./Sections";
import { HowItWorks, SmartCareSection, FinalCTA, Footer } from "./Closing";
import "./landing.css";

export default function LandingPage() {
  return (
    <div className="landing-page min-h-screen overflow-x-hidden bg-paper text-ink">
      <Navbar />
      <main>
        <Hero />
        <ValueStrip />
        <Features />
        <HowItWorks />
        <PatientDoctorSection />
        <SmartCareSection />
        <FinalCTA />
      </main>
      <Footer />
    </div>
  );
}