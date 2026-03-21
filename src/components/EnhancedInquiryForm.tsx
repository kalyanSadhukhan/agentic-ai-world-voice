import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { useState } from "react";
import { toast } from "sonner";
import { sendEnhancedInquiry } from "@/services/emailService";

interface EnhancedInquiryFormProps {
  onClose: () => void;
}

const EnhancedInquiryForm = ({ onClose }: EnhancedInquiryFormProps) => {
  const [formData, setFormData] = useState({
    name: "",
    email: "",
    company: "",
    message: "",
    primaryGoal: "",
    agentTypes: [] as string[]
  });

  const [isSubmitting, setIsSubmitting] = useState(false);

  const agentTypes = [
    "Appointment Setter Agent",
    "Customer Support Agent", 
    "Lead Capturing Agent",
    "Lead Qualification Agent",
    "Lead Nurturing Agent",
    "Follow-up Agent",
    "Database Reactivation Agent",
    "Front Desk Agent",
    "Renewal Agent",
    "Not Sure / Need Help Choosing"
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      await sendEnhancedInquiry(formData);
      toast.success("Thank you for your detailed inquiry! We'll get back to you soon with personalized recommendations.");
      setFormData({ name: "", email: "", company: "", message: "", primaryGoal: "", agentTypes: [] });
      onClose();
    } catch (error) {
      toast.error("Failed to send inquiry. Please try again or contact us directly at sales@voiceaiagentpro.ai");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleAgentTypeChange = (agentType: string, checked: boolean) => {
    if (checked) {
      setFormData({
        ...formData,
        agentTypes: [...formData.agentTypes, agentType]
      });
    } else {
      setFormData({
        ...formData,
        agentTypes: formData.agentTypes.filter(type => type !== agentType)
      });
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <Card className="w-full max-w-4xl max-h-[90vh] overflow-y-auto shadow-2xl">
        <CardContent className="p-8">
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-3xl font-bold text-gray-900">Ready to Transform Your Business?</h2>
            <Button 
              variant="ghost" 
              onClick={onClose}
              className="text-gray-500 hover:text-gray-700"
              disabled={isSubmitting}
            >
              ✕
            </Button>
          </div>
          
          <form onSubmit={handleSubmit} className="space-y-8">
            <div>
              <label className="block text-lg font-semibold text-gray-900 mb-4">
                What type of voice/AI agent are you interested in? *
              </label>
              <div className="grid md:grid-cols-2 gap-3">
                {agentTypes.map((agentType) => (
                  <div key={agentType} className="flex items-center space-x-3">
                    <Checkbox
                      id={agentType}
                      checked={formData.agentTypes.includes(agentType)}
                      onCheckedChange={(checked) => handleAgentTypeChange(agentType, checked as boolean)}
                    />
                    <label htmlFor={agentType} className="text-sm text-gray-700 cursor-pointer">
                      {agentType}
                    </label>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <label htmlFor="primaryGoal" className="block text-lg font-semibold text-gray-900 mb-4">
                What is the primary goal you want to achieve with a voice/AI agent? *
              </label>
              <Textarea
                id="primaryGoal"
                name="primaryGoal"
                value={formData.primaryGoal}
                onChange={handleInputChange}
                required
                rows={4}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="e.g., book more appointments, reduce manual follow-ups, 24/7 customer support, etc."
              />
            </div>

            <div className="grid md:grid-cols-2 gap-6">
              <div>
                <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-2">
                  Full Name *
                </label>
                <Input
                  type="text"
                  id="name"
                  name="name"
                  value={formData.name}
                  onChange={handleInputChange}
                  required
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="John Doe"
                />
              </div>
              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
                  Email Address *
                </label>
                <Input
                  type="email"
                  id="email"
                  name="email"
                  value={formData.email}
                  onChange={handleInputChange}
                  required
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="john@company.com"
                />
              </div>
            </div>

            <div>
              <label htmlFor="company" className="block text-sm font-medium text-gray-700 mb-2">
                Company Name *
              </label>
              <Input
                type="text"
                id="company"
                name="company"
                value={formData.company}
                onChange={handleInputChange}
                required
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Your Company"
              />
            </div>

            <div>
              <label htmlFor="message" className="block text-sm font-medium text-gray-700 mb-2">
                Additional Information
              </label>
              <Textarea
                id="message"
                name="message"
                value={formData.message}
                onChange={handleInputChange}
                rows={4}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Tell us more about your specific needs and current challenges..."
              />
            </div>

            <div className="flex gap-4">
              <Button
                type="submit"
                disabled={isSubmitting}
                className="flex-1 bg-blue-600 hover:bg-blue-700 text-white py-4 text-lg font-medium rounded-lg transition-colors disabled:opacity-50"
              >
                {isSubmitting ? "Sending..." : "Send Detailed Inquiry"}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={onClose}
                disabled={isSubmitting}
                className="px-8 py-4 text-lg font-medium rounded-lg"
              >
                Cancel
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
};

export default EnhancedInquiryForm;
